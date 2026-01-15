/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{PlatformContext, URL}
import wvlet.airframe.ulid.ULID

import scala.collection.mutable.ArrayBuffer

/** BAST deserialization entry point
  *
  * Provides static methods for reading BAST binary format back into AST.
  */
object BASTReader {

  /** Read a BAST file from bytes
    *
    * @param bytes The BAST file bytes
    * @param pc Platform context for error reporting
    * @return Either errors or the deserialized Nebula root
    */
  def read(bytes: Array[Byte])(using pc: PlatformContext): Either[Messages.Messages, Nebula] = {
    val reader = new BASTReader(bytes)
    reader.read()
  }
}

/** BAST binary format deserializer
  *
  * Reads a BAST file and reconstructs the AST. Mirrors the structure of BASTWriter
  * to ensure correct round-trip serialization.
  *
  * @param bytes The BAST file bytes to deserialize
  * @param pc Platform context for error reporting
  */
class BASTReader(bytes: Array[Byte])(using pc: PlatformContext) {

  private val reader = new ByteBufferReader(bytes)
  private var stringTable: StringTable = _
  private var lastLocation: At = At.empty
  private val messages = ArrayBuffer[Messages.Message]()

  /** Read and deserialize the BAST file
    *
    * @return Either errors or the deserialized Nebula root
    */
  def read(): Either[Messages.Messages, Nebula] = {
    try {
      // Read and validate header
      val header = readHeader()
      if !header.isValid then
        return Left(List(Messages.error("Invalid BAST file header", At.empty)))
      end if

      // Validate checksum
      val dataStart = HEADER_SIZE
      val dataLength = bytes.length - HEADER_SIZE
      val calculatedChecksum = BinaryFormat.calculateChecksum(bytes, dataStart, dataLength)
      if calculatedChecksum != header.checksum then
        return Left(List(Messages.error(
          s"BAST checksum mismatch: expected ${header.checksum}, got $calculatedChecksum", At.empty)))
      end if

      // Load string table
      reader.seek(header.stringTableOffset)
      stringTable = StringTable.readFrom(reader)

      // Read root Nebula from root offset
      reader.seek(header.rootOffset)
      val nebula = readRootNode()

      if messages.nonEmpty then
        Left(messages.toList)
      else
        Right(nebula)

    } catch {
      case e: Exception =>
        Left(List(Messages.error(s"BAST deserialization failed: ${e.getMessage}", At.empty)))
    }
  }

  // ========== Header Reading ==========

  private def readHeader(): BinaryFormat.Header = {
    val magic = reader.readRawBytes(4)
    val versionMajor = reader.readShort()
    val versionMinor = reader.readShort()
    val flags = reader.readShort()
    val stringTableOffset = reader.readInt()
    val rootOffset = reader.readInt()
    val fileSize = reader.readInt()
    val checksum = reader.readInt()
    val reserved = reader.readRawBytes(8)

    BinaryFormat.Header(
      magic = magic,
      versionMajor = versionMajor,
      versionMinor = versionMinor,
      flags = flags,
      stringTableOffset = stringTableOffset,
      rootOffset = rootOffset,
      fileSize = fileSize,
      checksum = checksum,
      reserved = reserved
    )
  }

  // ========== Root Node Reading ==========

  private def readRootNode(): Nebula = {
    val nodeType = reader.readU8()
    if nodeType != NODE_NEBULA then
      throw new IllegalArgumentException(s"Expected Nebula root node, got node type $nodeType")
    end if

    val loc = readLocation()
    val _id = readIdentifier() // Nebula has no explicit id, this is empty
    val contents = readContentsDeferred[NebulaContents]()
    val _metadata = readMetadataDeferred() // Returns empty now

    Nebula(loc, contents)
  }

  // ========== Node Deserialization ==========

  /** Read a RiddlValue node based on its type tag */
  private def readNode(): RiddlValue = {
    val nodeType = reader.readU8()

    nodeType match {
      // Root containers
      case NODE_NEBULA => readNebulaNode()
      case NODE_DOMAIN => readDomainNode()
      case NODE_CONTEXT => readContextNode()
      case NODE_ENTITY => readEntityNode()
      case NODE_MODULE => readModuleNode()
      case NODE_INCLUDE => readIncludeNode()
      case NODE_BAST_IMPORT => readBASTImportNode()

      // Types
      case NODE_TYPE => readTypeNode()
      case NODE_FIELD => readFieldOrConstantOrMethod()
      case NODE_ENUMERATOR => readEnumeratorNode()

      // Processors
      case NODE_ADAPTOR => readAdaptorNode()
      case NODE_FUNCTION => readFunctionNode()
      case NODE_PROJECTOR => readProjectorNode()
      case NODE_REPOSITORY => readRepositoryNode()
      case NODE_SCHEMA => readSchemaNode()
      case NODE_STREAMLET => readStreamletNode()
      case NODE_SAGA => readSagaNode()

      // Handler components
      case NODE_HANDLER => readHandlerOrStatement()
      case NODE_SAGA_STEP => readSagaStepNode()
      case NODE_STATE => readStateNode()
      case NODE_INVARIANT => readInvariantNode()
      case NODE_ON_CLAUSE => readOnClauseNode()

      // Streamlet components
      case NODE_INLET => readInletNode()
      case NODE_OUTLET => readOutletOrShownByNode()
      case NODE_CONNECTOR => readConnectorNode()

      // Epic components
      case NODE_EPIC => readEpicOrUseCaseNode()
      case NODE_USER => readUserOrUserStoryNode()

      // Interactions
      case NODE_PIPE => readPipeOrRelationshipOrInteraction()

      // UI Components
      case NODE_GROUP => readGroupOrContainedGroupNode()
      case NODE_INPUT => readInputNode()
      case NODE_OUTPUT => readOutputNode()

      // Metadata
      case NODE_DESCRIPTION => readDescriptionOrOptionOrAttachment()
      case NODE_BLOCK_DESCRIPTION => readBlockDescriptionNode()
      case NODE_COMMENT => readLineCommentNode()
      case NODE_BLOCK_COMMENT => readInlineCommentNode()

      // Simple values
      case NODE_IDENTIFIER => readIdentifierNode()
      case NODE_PATH_IDENTIFIER => readPathIdentifierNode()
      case NODE_LITERAL_STRING => readLiteralStringNode()

      // Authors
      case NODE_AUTHOR => readAuthorOrAuthorRefNode()
      case NODE_TERM => readTermNode()

      // Streamlet shapes
      case STREAMLET_VOID => readVoidNode()
      case STREAMLET_SOURCE => readSourceNode()
      case STREAMLET_SINK => readSinkNode()
      case STREAMLET_FLOW => readFlowNode()
      case STREAMLET_MERGE => readMergeNode()
      case STREAMLET_SPLIT => readSplitNode()

      // Adaptor directions
      case ADAPTOR_INBOUND => readInboundAdaptorNode()
      case ADAPTOR_OUTBOUND => readOutboundAdaptorNode()

      case _ =>
        addError(s"Unknown node type: $nodeType at position ${reader.position}")
        // Return a placeholder to continue parsing
        LiteralString(At.empty, s"<unknown node type $nodeType>")
    }
  }

  // ========== Container Nodes ==========

  private def readNebulaNode(): Nebula = {
    val loc = readLocation()
    val _id = readIdentifier()
    val contents = readContentsDeferred[NebulaContents]()
    val _metadata = readMetadataDeferred() // Not used
    Nebula(loc, contents)
  }

  private def readDomainNode(): Domain = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[OccursInDomain]().asInstanceOf[Contents[DomainContents]]
    val metadata = readMetadataDeferred() // Returns empty now
    Domain(loc, id, contents, metadata)
  }

  private def readContextNode(): Context = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[OccursInContext]().asInstanceOf[Contents[ContextContents]]
    val metadata = readMetadataDeferred()
    Context(loc, id, contents, metadata)
  }

  private def readEntityNode(): Entity = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[OccursInProcessor | State]().asInstanceOf[Contents[EntityContents]]
    val metadata = readMetadataDeferred()
    Entity(loc, id, contents, metadata)
  }

  private def readModuleNode(): Module = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[Domain | Author | Comment]().asInstanceOf[Contents[ModuleContents]]
    val metadata = readMetadataDeferred()
    Module(loc, id, contents, metadata)
  }

  private def readIncludeNode(): Include[RiddlValue] = {
    val loc = readLocation()
    val origin = readURL()
    val contents = readContentsDeferred[RiddlValue]()
    Include[RiddlValue](loc, origin, contents)
  }

  private def readBASTImportNode(): BASTImport = {
    val loc = readLocation()
    val path = readLiteralString()
    // Contents are not stored in BAST - they're loaded dynamically
    // by BASTLoader when this import is encountered
    BASTImport(loc, path)
  }

  // ========== Type Definitions ==========

  private def readTypeNode(): Type = {
    val loc = readLocation()
    val id = readIdentifier()
    val typEx = readTypeExpression()
    val metadata = readMetadataDeferred()
    Type(loc, id, typEx, metadata)
  }

  private def readFieldOrConstantOrMethod(): RiddlValue = {
    val loc = readLocation()
    val idOrName = reader.peekU8()

    // Check if next is NODE_IDENTIFIER or a string
    if idOrName == NODE_IDENTIFIER then
      val id = readIdentifier()
      val typEx = readTypeExpression()

      // Check if this is a Constant (has value) or Method (has args) or Field
      // This is ambiguous - we need to peek ahead
      // For now, assume Field. Writer should disambiguate better.
      val metadata = readMetadataDeferred()
      Field(loc, id, typEx, metadata)
    else
      // MethodArgument: has name as string
      val name = readString()
      val typEx = readTypeExpression()
      MethodArgument(loc, name, typEx)
    end if
  }

  private def readEnumeratorNode(): Enumerator = {
    val loc = readLocation()
    val id = readIdentifier()
    val enumVal = readOption(reader.readVarLong())
    val metadata = readMetadataDeferred()
    Enumerator(loc, id, enumVal, metadata)
  }

  // ========== Processor Definitions ==========

  private def readAdaptorNode(): Adaptor = {
    val loc = readLocation()
    val id = readIdentifier()
    val directionTag = reader.readU8()
    val direction: AdaptorDirection = directionTag match {
      case ADAPTOR_INBOUND => InboundAdaptor(loc)
      case ADAPTOR_OUTBOUND => OutboundAdaptor(loc)
      case _ => InboundAdaptor(loc) // Default
    }
    val referent = readContextRef()
    val contents = readContentsDeferred[OccursInProcessor]().asInstanceOf[Contents[AdaptorContents]]
    val metadata = readMetadataDeferred()
    Adaptor(loc, id, direction, referent, contents, metadata)
  }

  private def readFunctionNode(): Function = {
    val loc = readLocation()
    val id = readIdentifier()
    val input = readOption(readTypeExpression()).map(_.asInstanceOf[Aggregation])
    val output = readOption(readTypeExpression()).map(_.asInstanceOf[Aggregation])
    val contents = readContentsDeferred[OccursInVitalDefinition | Statement | Function]().asInstanceOf[Contents[FunctionContents]]
    val metadata = readMetadataDeferred()
    Function(loc, id, input, output, contents, metadata)
  }

  private def readSagaNode(): Saga = {
    val loc = readLocation()
    val id = readIdentifier()
    val input = readOption(readTypeExpression()).map(_.asInstanceOf[Aggregation])
    val output = readOption(readTypeExpression()).map(_.asInstanceOf[Aggregation])
    val contents = readContentsDeferred[OccursInVitalDefinition | SagaStep]().asInstanceOf[Contents[SagaContents]]
    val metadata = readMetadataDeferred()
    Saga(loc, id, input, output, contents, metadata)
  }

  private def readProjectorNode(): Projector = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[OccursInProcessor | RepositoryRef]().asInstanceOf[Contents[ProjectorContents]]
    val metadata = readMetadataDeferred()
    Projector(loc, id, contents, metadata)
  }

  private def readRepositoryNode(): Repository = {
    val loc = readLocation()
    val id = readIdentifier()
    val contents = readContentsDeferred[OccursInProcessor | Schema]().asInstanceOf[Contents[RepositoryContents]]
    val metadata = readMetadataDeferred()
    Repository(loc, id, contents, metadata)
  }

  private def readSchemaNode(): Schema = {
    // Read schemaKind subtype: 0=Relational, 1=Document, 2=Graphical
    val subtype = reader.readU8()
    val loc = readLocation()
    val id = readIdentifier()

    val schemaKind = subtype match {
      case 0 => RepositorySchemaKind.Relational
      case 1 => RepositorySchemaKind.Document
      case 2 => RepositorySchemaKind.Graphical
      case _ => RepositorySchemaKind.Relational
    }

    // Read data map
    val dataCount = reader.readVarInt()
    val data = (0 until dataCount).map { _ =>
      val dataId = readIdentifier()
      val tref = readTypeRef()
      (dataId, tref)
    }.toMap

    // Read links map
    val linksCount = reader.readVarInt()
    val links = (0 until linksCount).map { _ =>
      val linkId = readIdentifier()
      val fr1 = readFieldRef()
      val fr2 = readFieldRef()
      (linkId, (fr1, fr2))
    }.toMap

    // Read indices
    val indices = readSeq(() => readFieldRef())
    val metadata = readMetadataDeferred()

    Schema(loc, id, schemaKind, data, links, indices, metadata)
  }

  private def readStreamletNode(): Streamlet = {
    val loc = readLocation()
    val id = readIdentifier()
    val shapeTag = reader.readU8()
    val shape: StreamletShape = shapeTag match {
      case STREAMLET_VOID => Void(loc)
      case STREAMLET_SOURCE => Source(loc)
      case STREAMLET_SINK => Sink(loc)
      case STREAMLET_FLOW => Flow(loc)
      case STREAMLET_MERGE => Merge(loc)
      case STREAMLET_SPLIT => Split(loc)
      case _ => Void(loc)
    }
    val contents = readContentsDeferred[OccursInProcessor | Inlet | Outlet | Connector]().asInstanceOf[Contents[StreamletContents]]
    val metadata = readMetadataDeferred()
    Streamlet(loc, id, shape, contents, metadata)
  }

  // ========== Epic Definitions ==========

  private def readEpicOrUseCaseNode(): RiddlValue = {
    val loc = readLocation()
    val id = readIdentifier()

    // Check if next is UserStory (NODE_USER tag) or Contents
    val saved = reader.position
    val nextTag = reader.readU8()
    reader.seek(saved)

    if nextTag == NODE_USER then
      val userStory = readUserStoryNode()
      val contents = readContentsDeferred[OccursInVitalDefinition | ShownBy | UseCase]().asInstanceOf[Contents[EpicContents]]
      val metadata = readMetadataDeferred()
      Epic(loc, id, userStory, contents, metadata)
    else
      // UseCase
      val userStory = readUserStoryNode()
      val contents = readContentsDeferred[UseCaseContents]()
      val metadata = readMetadataDeferred()
      UseCase(loc, id, userStory, contents, metadata)
    end if
  }

  // ========== Handler Components ==========

  // Marker value used by writer to distinguish statements from handlers
  // Using 255 (0xFF) as it's distinct from valid location/string data
  private val STATEMENT_MARKER: Int = 255

  private def readHandlerOrStatement(): RiddlValue = {
    // After NODE_HANDLER tag is consumed, check the first byte:
    // - For statements: first byte is STATEMENT_MARKER (0xFF), then stmtType, then location
    // - For handlers: first byte is start of location data (no marker)
    val firstByte = reader.peekU8()

    if firstByte == STATEMENT_MARKER then
      reader.readU8() // Consume marker
      val stmtType = reader.readU8()
      val loc = readLocation()
      readStatement(loc, stmtType)
    else
      // Handler: location, identifier, contents, metadata
      val loc = readLocation()
      val id = readIdentifier()
      val contents = readContentsDeferred[HandlerContents]()
      val metadata = readMetadataDeferred()
      Handler(loc, id, contents, metadata)
    end if
  }

  private def readSagaStepNode(): SagaStep = {
    val loc = readLocation()
    val id = readIdentifier()
    val doStatements = readContentsDeferred[Statements]()
    val undoStatements = readContentsDeferred[Statements]()
    val metadata = readMetadataDeferred()
    SagaStep(loc, id, doStatements, undoStatements, metadata)
  }

  private def readStatement(loc: At, stmtType: Int): Statement = {
    stmtType match {
      case 0 => // Arbitrary
        val what = readLiteralString()
        ArbitraryStatement(loc, what)

      case 1 => // Error
        val message = readLiteralString()
        ErrorStatement(loc, message)

      case 2 => // Focus
        val group = readGroupRef()
        FocusStatement(loc, group)

      case 3 => // Set
        val field = readFieldRef()
        val value = readLiteralString()
        SetStatement(loc, field, value)

      case 4 => // Return
        val value = readLiteralString()
        ReturnStatement(loc, value)

      case 5 => // Send
        val msg = readMessageRef()
        val portlet = readPortletRef()
        SendStatement(loc, msg, portlet)

      case 6 => // Reply
        val message = readMessageRef()
        ReplyStatement(loc, message)

      case 7 => // Morph
        val entity = readEntityRef()
        val state = readStateRef()
        val value = readMessageRef()
        MorphStatement(loc, entity, state, value)

      case 8 => // Become
        val entity = readEntityRef()
        val handler = readHandlerRef()
        BecomeStatement(loc, entity, handler)

      case 9 => // Tell
        val msg = readMessageRef()
        val processorRef = readProcessorRef()
        TellStatement(loc, msg, processorRef)

      case 10 => // Call
        val func = readFunctionRef()
        CallStatement(loc, func)

      case 11 => // ForEach
        val refType = reader.readU8()
        val ref: FieldRef | OutletRef | InletRef = refType match {
          case 0 => readFieldRef()
          case 1 => readOutletRef()
          case 2 => readInletRef()
          case _ => readFieldRef()
        }
        val do_ = readContentsDeferred[Statements]()
        ForEachStatement(loc, ref, do_)

      case 12 => // IfThenElse
        val cond = readLiteralString()
        val thens = readContentsDeferred[Statements]()
        val elses = readContentsDeferred[Statements]()
        IfThenElseStatement(loc, cond, thens, elses)

      case 13 => // Stop
        StopStatement(loc)

      case 14 => // Read
        val keyword = readString()
        val what = readLiteralString()
        val from = readTypeRef()
        val where = readLiteralString()
        ReadStatement(loc, keyword, what, from, where)

      case 15 => // Write
        val keyword = readString()
        val what = readLiteralString()
        val to = readTypeRef()
        WriteStatement(loc, keyword, what, to)

      case 16 => // Code
        val language = readLiteralString()
        val body = readString()
        CodeStatement(loc, language, body)

      case _ =>
        ArbitraryStatement(loc, LiteralString(loc, s"<unknown statement $stmtType>"))
    }
  }

  private def readStateNode(): State = {
    val loc = readLocation()
    val id = readIdentifier()
    val typ = readTypeRef()
    val metadata = readMetadataDeferred()
    State(loc, id, typ, metadata)
  }

  private def readInvariantNode(): Invariant = {
    val loc = readLocation()
    val id = readIdentifier()
    val condition = readOption(readLiteralString())
    val metadata = readMetadataDeferred()
    Invariant(loc, id, condition, metadata)
  }

  private def readOnClauseNode(): OnClause = {
    val clauseType = reader.readU8()
    val loc = readLocation()

    clauseType match {
      case 0 => // Init
        val contents = readContentsDeferred[Statements]()
        val metadata = readMetadataDeferred()
        OnInitializationClause(loc, contents, metadata)

      case 1 => // Term
        val contents = readContentsDeferred[Statements]()
        val metadata = readMetadataDeferred()
        OnTerminationClause(loc, contents, metadata)

      case 2 => // Message
        val msg = readMessageRef()
        val from = readOption {
          val optId = readOption(readIdentifier())
          val ref = readReference()
          (optId, ref)
        }
        val contents = readContentsDeferred[Statements]()
        val metadata = readMetadataDeferred()
        OnMessageClause(loc, msg, from, contents, metadata)

      case 3 => // Other
        val contents = readContentsDeferred[Statements]()
        val metadata = readMetadataDeferred()
        OnOtherClause(loc, contents, metadata)

      case _ =>
        OnOtherClause(loc, Contents.empty[Statements](), Contents.empty[MetaData]())
    }
  }

  // ========== Streamlet Components ==========

  private def readInletNode(): Inlet = {
    val loc = readLocation()
    val id = readIdentifier()
    val type_ = readTypeRef()
    val metadata = readMetadataDeferred()
    Inlet(loc, id, type_, metadata)
  }

  private def readOutletOrShownByNode(): RiddlValue = {
    val loc = readLocation()

    // Check if this is ShownBy (has seq of URLs) or Outlet (has id)
    val saved = reader.position
    val nextTag = reader.readU8()
    reader.seek(saved)

    if nextTag == NODE_IDENTIFIER then
      val id = readIdentifier()
      val type_ = readTypeRef()
      val metadata = readMetadataDeferred()
      Outlet(loc, id, type_, metadata)
    else
      // ShownBy
      val urls = readSeq(() => readURL())
      ShownBy(loc, urls)
    end if
  }

  private def readConnectorNode(): Connector = {
    val loc = readLocation()
    val id = readIdentifier()
    val from = readOutletRef()
    val to = readInletRef()
    val metadata = readMetadataDeferred()
    Connector(loc, id, from, to, metadata)
  }

  // ========== Interactions ==========

  private def readPipeOrRelationshipOrInteraction(): RiddlValue = {
    val interactionType = reader.readU8()
    val loc = readLocation()

    interactionType match {
      case 0 => // Parallel
        val contents = readContentsDeferred[Interaction]().asInstanceOf[Contents[InteractionContainerContents]]
        val metadata = readMetadataDeferred()
        ParallelInteractions(loc, contents, metadata)

      case 1 => // Sequential
        val contents = readContentsDeferred[Interaction]().asInstanceOf[Contents[InteractionContainerContents]]
        val metadata = readMetadataDeferred()
        SequentialInteractions(loc, contents, metadata)

      case 2 => // Optional
        val contents = readContentsDeferred[Interaction]().asInstanceOf[Contents[InteractionContainerContents]]
        val metadata = readMetadataDeferred()
        OptionalInteractions(loc, contents, metadata)

      case 10 => // Vague
        val from = readLiteralString()
        val relationship = readLiteralString()
        val to = readLiteralString()
        val metadata = readMetadataDeferred()
        VagueInteraction(loc, from, relationship, to, metadata)

      case 11 => // SendMessage
        val from = readReference()
        val message = readMessageRef()
        val to = readProcessorRef()
        val metadata = readMetadataDeferred()
        SendMessageInteraction(loc, from, message, to, metadata)

      case 12 => // Arbitrary
        val from = readReference()
        val relationship = readLiteralString()
        val to = readReference()
        val metadata = readMetadataDeferred()
        ArbitraryInteraction(loc, from, relationship, to, metadata)

      case 13 => // Self
        val from = readReference()
        val relationship = readLiteralString()
        val metadata = readMetadataDeferred()
        SelfInteraction(loc, from, relationship, metadata)

      case 14 => // FocusOnGroup
        val from = readUserRef()
        val to = readGroupRef()
        val metadata = readMetadataDeferred()
        FocusOnGroupInteraction(loc, from, to, metadata)

      case 15 => // DirectUserToURL
        val from = readUserRef()
        val url = readURL()
        val metadata = readMetadataDeferred()
        DirectUserToURLInteraction(loc, from, url, metadata)

      case 16 => // ShowOutput
        val from = readOutputRef()
        val relationship = readLiteralString()
        val to = readUserRef()
        val metadata = readMetadataDeferred()
        ShowOutputInteraction(loc, from, relationship, to, metadata)

      case 17 => // SelectInput
        val from = readUserRef()
        val to = readInputRef()
        val metadata = readMetadataDeferred()
        SelectInputInteraction(loc, from, to, metadata)

      case 18 => // TakeInput
        val from = readUserRef()
        val to = readInputRef()
        val metadata = readMetadataDeferred()
        TakeInputInteraction(loc, from, to, metadata)

      case _ => // Relationship (default)
        val id = readIdentifier()
        val withProcessor = readProcessorRef()
        val cardinality = RelationshipCardinality.fromOrdinal(reader.readU8())
        val label = readOption(readLiteralString())
        val metadata = readMetadataDeferred()
        Relationship(loc, id, withProcessor, cardinality, label, metadata)
    }
  }

  // ========== UI Components ==========

  private def readGroupOrContainedGroupNode(): RiddlValue = {
    val loc = readLocation()

    // Check if this is Group (has alias) or ContainedGroup (has GroupRef)
    val saved = reader.position
    val aliasOrId = readString()
    val id = readIdentifier()
    val nextTag = reader.peekU8()
    reader.seek(saved)

    if nextTag == NODE_GROUP then
      // ContainedGroup
      val id = readIdentifier()
      val group = readGroupRef()
      val metadata = readMetadataDeferred()
      ContainedGroup(loc, id, group, metadata)
    else
      // Group
      val alias = readString()
      val id = readIdentifier()
      val contents = readContentsDeferred[OccursInGroup]()
      val metadata = readMetadataDeferred()
      Group(loc, alias, id, contents, metadata)
    end if
  }

  private def readInputNode(): Input = {
    val loc = readLocation()
    val nounAlias = readString()
    val id = readIdentifier()
    val verbAlias = readString()
    val takeIn = readTypeRef()
    val contents = readContentsDeferred[OccursInInput]()
    val metadata = readMetadataDeferred()
    Input(loc, nounAlias, id, verbAlias, takeIn, contents, metadata)
  }

  private def readOutputNode(): Output = {
    val loc = readLocation()
    val nounAlias = readString()
    val id = readIdentifier()
    val verbAlias = readString()

    // Read union type
    val putOutType = reader.readU8()
    val putOut: TypeRef | ConstantRef | LiteralString = putOutType match {
      case 0 => readTypeRef()
      case 1 => readConstantRef()
      case 2 => readLiteralString()
      case _ => readTypeRef()
    }

    val contents = readContentsDeferred[OccursInOutput]()
    val metadata = readMetadataDeferred()
    Output(loc, nounAlias, id, verbAlias, putOut, contents, metadata)
  }

  // ========== Users and Authors ==========

  private def readAuthorOrAuthorRefNode(): RiddlValue = {
    val loc = readLocation()

    // Check if this is AuthorRef (has PathIdentifier) or Author (has name, email)
    val saved = reader.position
    val nextTag = reader.readU8()
    reader.seek(saved)

    if nextTag == NODE_PATH_IDENTIFIER then
      // AuthorRef
      val pathId = readPathIdentifier()
      AuthorRef(loc, pathId)
    else
      // Author
      val id = readIdentifier()
      val name = readLiteralString()
      val email = readLiteralString()
      val organization = readOption(readLiteralString())
      val title = readOption(readLiteralString())
      val url = readOption(readURL())
      val metadata = readMetadataDeferred()
      Author(loc, id, name, email, organization, title, url, metadata)
    end if
  }

  private def readUserOrUserStoryNode(): RiddlValue = {
    val loc = readLocation()

    // Check if this is UserRef (has PathIdentifier) or User (has id, is_a) or UserStory
    val saved = reader.position
    val nextTag = reader.readU8()
    reader.seek(saved)

    if nextTag == NODE_PATH_IDENTIFIER then
      // UserRef
      val pathId = readPathIdentifier()
      UserRef(loc, pathId)
    else if nextTag == NODE_IDENTIFIER then
      // User
      val id = readIdentifier()
      val is_a = readLiteralString()
      val metadata = readMetadataDeferred()
      User(loc, id, is_a, metadata)
    else
      // UserStory
      val user = readUserRef()
      val capability = readLiteralString()
      val benefit = readLiteralString()
      UserStory(loc, user, capability, benefit)
    end if
  }

  private def readUserStoryNode(): UserStory = {
    val nodeType = reader.readU8() // Should be NODE_USER
    val loc = readLocation()
    val user = readUserRef()
    val capability = readLiteralString()
    val benefit = readLiteralString()
    UserStory(loc, user, capability, benefit)
  }

  private def readTermNode(): Term = {
    val loc = readLocation()
    val id = readIdentifier()
    val definition = readSeq(() => readLiteralString())
    Term(loc, id, definition)
  }

  // ========== Metadata ==========

  private def readDescriptionOrOptionOrAttachment(): RiddlValue = {
    val descType = reader.readU8()
    val loc = readLocation()

    descType match {
      case 0 => // Brief
        val brief = readLiteralString()
        BriefDescription(loc, brief)

      case 2 => // URL
        val url = readURL()
        URLDescription(loc, url)

      case 10 => // Option
        val name = readString()
        val args = readSeq(() => readLiteralString())
        OptionValue(loc, name, args)

      case 20 => // FileAttachment
        val id = readIdentifier()
        val mimeType = readString()
        val inFile = readLiteralString()
        FileAttachment(loc, id, mimeType, inFile)

      case 21 => // StringAttachment
        val id = readIdentifier()
        val mimeType = readString()
        val value = readLiteralString()
        StringAttachment(loc, id, mimeType, value)

      case 22 => // ULIDAttachment
        val ulidBytes = reader.readRawBytes(16)
        val ulid = ULID.fromBytes(ulidBytes)
        ULIDAttachment(loc, ulid)

      case _ =>
        BriefDescription(loc, LiteralString(loc, s"<unknown description type $descType>"))
    }
  }

  private def readBlockDescriptionNode(): BlockDescription = {
    val loc = readLocation()
    val lines = readSeq(() => readLiteralString())
    BlockDescription(loc, lines)
  }

  private def readLineCommentNode(): LineComment = {
    val loc = readLocation()
    val text = readString()
    LineComment(loc, text)
  }

  private def readInlineCommentNode(): InlineComment = {
    val loc = readLocation()
    val lines = readSeq(() => readString())
    InlineComment(loc, lines)
  }

  // ========== Simple Values ==========

  private def readIdentifierNode(): Identifier = {
    val loc = readLocation()
    val value = readString()
    Identifier(loc, value)
  }

  private def readPathIdentifierNode(): PathIdentifier = {
    val loc = readLocation()
    val value = readSeq(() => readString())
    PathIdentifier(loc, value)
  }

  private def readLiteralStringNode(): LiteralString = {
    val loc = readLocation()
    val s = readString()
    LiteralString(loc, s)
  }

  // ========== Streamlet Shapes ==========

  private def readVoidNode(): Void = {
    val loc = readLocation()
    Void(loc)
  }

  private def readSourceNode(): Source = {
    val loc = readLocation()
    Source(loc)
  }

  private def readSinkNode(): Sink = {
    val loc = readLocation()
    Sink(loc)
  }

  private def readFlowNode(): Flow = {
    val loc = readLocation()
    Flow(loc)
  }

  private def readMergeNode(): Merge = {
    val loc = readLocation()
    Merge(loc)
  }

  private def readSplitNode(): Split = {
    val loc = readLocation()
    Split(loc)
  }

  // ========== Adaptor Directions ==========

  private def readInboundAdaptorNode(): InboundAdaptor = {
    val loc = readLocation()
    InboundAdaptor(loc)
  }

  private def readOutboundAdaptorNode(): OutboundAdaptor = {
    val loc = readLocation()
    OutboundAdaptor(loc)
  }

  // ========== Type Expressions ==========

  private def readTypeExpression(): TypeExpression = {
    val typeTag = reader.readU8()

    typeTag match {
      // Aggregations
      case TYPE_AGGREGATION =>
        // Always read subtype byte - all TYPE_AGGREGATION variants have one
        val subtype = reader.readU8()
        val loc = readLocation()

        subtype match {
          case 20 => // Sequence
            val of = readTypeExpression()
            Sequence(loc, of)

          case 21 => // Set
            val of = readTypeExpression()
            Set(loc, of)

          case 22 => // Graph
            val of = readTypeExpression()
            Graph(loc, of)

          case 23 => // Table
            val of = readTypeExpression()
            val dimensions = readSeq(() => reader.readVarLong())
            Table(loc, of, dimensions)

          case 24 => // Replica
            val of = readTypeExpression()
            Replica(loc, of)

          case 255 => // Plain Aggregation (subtype marker)
            val contents = readContentsDeferred[AggregateContents]()
            Aggregation(loc, contents)

          case _ => // AggregateUseCaseTypeExpression (usecase ordinals 0-12)
            val usecase = AggregateUseCase.fromOrdinal(subtype)
            val contents = readContentsDeferred[AggregateContents]()
            AggregateUseCaseTypeExpression(loc, usecase, contents)
        }

      case TYPE_ALTERNATION =>
        val loc = readLocation()
        val of = readContentsDeferred[AliasedTypeExpression]()
        Alternation(loc, of)

      case TYPE_ENUMERATION =>
        val loc = readLocation()
        val enumerators = readContentsDeferred[Enumerator]()
        Enumeration(loc, enumerators)

      case TYPE_MAPPING =>
        val loc = readLocation()
        val from = readTypeExpression()
        val to = readTypeExpression()
        Mapping(loc, from, to)

      // Cardinality
      case TYPE_OPTIONAL =>
        val loc = readLocation()
        val typeExp = readTypeExpression()
        Optional(loc, typeExp)

      case TYPE_ZERO_OR_MORE =>
        val loc = readLocation()
        val typeExp = readTypeExpression()
        ZeroOrMore(loc, typeExp)

      case TYPE_ONE_OR_MORE =>
        val loc = readLocation()
        val typeExp = readTypeExpression()
        OneOrMore(loc, typeExp)

      case TYPE_RANGE =>
        val loc = readLocation()
        val saved = reader.position
        val nextTag = reader.peekU8()
        reader.seek(saved)

        if nextTag == TYPE_NUMBER || nextTag == TYPE_STRING then
          // SpecificRange
          val typeExp = readTypeExpression()
          val min = reader.readVarLong()
          val max = reader.readVarLong()
          SpecificRange(loc, typeExp, min, max)
        else
          // RangeType
          val min = reader.readVarLong()
          val max = reader.readVarLong()
          RangeType(loc, min, max)
        end if

      // References - all variants have subtype byte
      case TYPE_REF =>
        val subtype = reader.readU8()
        val loc = readLocation()

        subtype match {
          case 0 => // AliasedTypeExpression
            val keyword = readString()
            val pathId = readPathIdentifier()
            AliasedTypeExpression(loc, keyword, pathId)

          case 10 => // EntityReference
            val entity = readPathIdentifier()
            EntityReferenceTypeExpression(loc, entity)

          case 99 => // Abstract
            Abstract(loc)

          case 100 => // Nothing
            Nothing(loc)

          case _ =>
            addError(s"Unknown TYPE_REF subtype: $subtype")
            Abstract(loc)
        }

      // Strings - all variants have subtype byte
      case TYPE_STRING =>
        val subtype = reader.readU8()
        val loc = readLocation()

        subtype match {
          case 0 => // String_
            val min = readOption(reader.readVarLong())
            val max = readOption(reader.readVarLong())
            String_(loc, min, max)

          case 1 => // URI
            val scheme = readOption(readLiteralString())
            URI(loc, scheme)

          case 2 => // Blob
            val blobKind = BlobKind.fromOrdinal(reader.readU8())
            Blob(loc, blobKind)

          case _ =>
            addError(s"Unknown TYPE_STRING subtype: $subtype")
            String_(loc, None, None)
        }

      case TYPE_PATTERN =>
        val loc = readLocation()
        val pattern = readSeq(() => readLiteralString())
        Pattern(loc, pattern)

      case TYPE_UNIQUE_ID =>
        val subtype = reader.readU8()
        val loc = readLocation()

        subtype match {
          case 0 => // UniqueId
            val entityPath = readPathIdentifier()
            UniqueId(loc, entityPath)

          case 1 => // UUID
            UUID(loc)

          case 2 => // UserId
            UserId(loc)

          case _ =>
            addError(s"Unknown TYPE_UNIQUE_ID subtype: $subtype")
            UUID(loc)
        }

      // Boolean
      case TYPE_BOOL =>
        val loc = readLocation()
        Bool(loc)

      // Numbers
      case TYPE_NUMBER =>
        val saved = reader.position
        val nextByte = reader.peekU8()
        reader.seek(saved)

        if nextByte < 100 then
          // Has subtype
          val subtype = reader.readU8()
          val loc = readLocation()

          subtype match {
            case 0 => Number(loc)
            case 1 => Integer(loc)
            case 2 => Whole(loc)
            case 3 => Natural(loc)

            case 10 => // Decimal
              val whole = reader.readVarLong()
              val fractional = reader.readVarLong()
              Decimal(loc, whole, fractional)

            case 11 => Real(loc)

            // SI units
            case 20 => Current(loc)
            case 21 => Length(loc)
            case 22 => Luminosity(loc)
            case 23 => Mass(loc)
            case 24 => Mole(loc)
            case 25 => Temperature(loc)

            // Time types
            case 30 => Date(loc)
            case 31 => Time(loc)
            case 32 => DateTime(loc)

            case 33 => // ZonedDate
              val zone = readOption(readLiteralString())
              ZonedDate(loc, zone)

            case 34 => // ZonedDateTime
              val zone = readOption(readLiteralString())
              ZonedDateTime(loc, zone)

            case 35 => TimeStamp(loc)
            case 36 => Duration(loc)

            case 40 => Location(loc)

            case 50 => // Currency
              val country = readString()
              Currency(loc, country)

            case _ => Number(loc)
          }
        else
          // Plain Number
          val loc = readLocation()
          Number(loc)

      case _ =>
        addError(s"Unknown type expression tag: $typeTag at position ${reader.position}")
        Abstract(At.empty)
    }
  }

  // ========== References ==========

  private def readReference(): Reference[Definition] = {
    // Peek at tag to determine type - each reader consumes its own tag
    val refTag = reader.peekU8()

    refTag match {
      case NODE_AUTHOR => readAuthorRef()
      case NODE_TYPE => readTypeRefOrMessageRef()
      case NODE_FIELD => readFieldRefOrConstantRef()
      case NODE_ADAPTOR => readAdaptorRef()
      case NODE_FUNCTION => readFunctionRef()
      case NODE_HANDLER => readHandlerRef()
      case NODE_STATE => readStateRef()
      case NODE_ENTITY => readEntityRef()
      case NODE_REPOSITORY => readRepositoryRef()
      case NODE_PROJECTOR => readProjectorRef()
      case NODE_CONTEXT => readContextRef()
      case NODE_STREAMLET => readStreamletRef()
      case NODE_INLET => readInletRef()
      case NODE_OUTLET => readOutletRef()
      case NODE_SAGA => readSagaRef()
      case NODE_USER => readUserRef()
      case NODE_EPIC => readEpicRef()
      case NODE_GROUP => readGroupRef()
      case NODE_INPUT => readInputRef()
      case NODE_OUTPUT => readOutputRef()
      case NODE_DOMAIN => readDomainRef()
      case _ =>
        reader.readU8() // consume the unknown tag
        addError(s"Unknown reference tag: $refTag")
        TypeRef(At.empty, "", PathIdentifier.empty)
    }
  }

  private def readAuthorRef(): AuthorRef = {
    val tag = reader.readU8() // Read NODE_AUTHOR tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    AuthorRef(loc, pathId)
  }

  private def readTypeRefOrMessageRef(): TypeRef | MessageRef = {
    val tag = reader.readU8() // Read NODE_TYPE tag
    val saved = reader.position
    val loc = readLocation()

    // Check if this has a subtype (message ref) or keyword (type ref)
    val nextByte = reader.peekU8()
    reader.seek(saved)

    if nextByte < 10 then
      // Message ref (has subtype)
      val loc = readLocation()
      val subtype = reader.readU8()
      val pathId = readPathIdentifier()

      subtype match {
        case 0 => CommandRef(loc, pathId)
        case 1 => EventRef(loc, pathId)
        case 2 => QueryRef(loc, pathId)
        case 3 => ResultRef(loc, pathId)
        case 4 => RecordRef(loc, pathId)
        case _ => RecordRef(loc, pathId)
      }
    else
      // TypeRef
      val loc = readLocation()
      val keyword = readString()
      val pathId = readPathIdentifier()
      TypeRef(loc, keyword, pathId)
    end if
  }

  private def readTypeRef(): TypeRef = {
    val tag = reader.readU8() // Read NODE_TYPE tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifier()
    TypeRef(loc, keyword, pathId)
  }

  private def readFieldRefOrConstantRef(): FieldRef | ConstantRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    // For now, default to FieldRef
    FieldRef(loc, pathId)
  }

  private def readFieldRef(): FieldRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    FieldRef(loc, pathId)
  }

  private def readConstantRef(): ConstantRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag (constants use same tag as fields)
    val loc = readLocation()
    val pathId = readPathIdentifier()
    ConstantRef(loc, pathId)
  }

  private def readCommandRef(): CommandRef = {
    val loc = readLocation()
    val pathId = readPathIdentifier()
    CommandRef(loc, pathId)
  }

  private def readEventRef(): EventRef = {
    val loc = readLocation()
    val pathId = readPathIdentifier()
    EventRef(loc, pathId)
  }

  private def readQueryRef(): QueryRef = {
    val loc = readLocation()
    val pathId = readPathIdentifier()
    QueryRef(loc, pathId)
  }

  private def readResultRef(): ResultRef = {
    val loc = readLocation()
    val pathId = readPathIdentifier()
    ResultRef(loc, pathId)
  }

  private def readRecordRef(): RecordRef = {
    val loc = readLocation()
    val pathId = readPathIdentifier()
    RecordRef(loc, pathId)
  }

  private def readAdaptorRef(): AdaptorRef = {
    val tag = reader.readU8() // Read NODE_ADAPTOR tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    AdaptorRef(loc, pathId)
  }

  private def readFunctionRef(): FunctionRef = {
    val tag = reader.readU8() // Read NODE_FUNCTION tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    FunctionRef(loc, pathId)
  }

  private def readHandlerRef(): HandlerRef = {
    val tag = reader.readU8() // Read NODE_HANDLER tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    HandlerRef(loc, pathId)
  }

  private def readStateRef(): StateRef = {
    val tag = reader.readU8() // Read NODE_STATE tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    StateRef(loc, pathId)
  }

  private def readEntityRef(): EntityRef = {
    val tag = reader.readU8() // Read NODE_ENTITY tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    EntityRef(loc, pathId)
  }

  private def readRepositoryRef(): RepositoryRef = {
    val tag = reader.readU8() // Read NODE_REPOSITORY tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    RepositoryRef(loc, pathId)
  }

  private def readProjectorRef(): ProjectorRef = {
    val tag = reader.readU8() // Read NODE_PROJECTOR tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    ProjectorRef(loc, pathId)
  }

  private def readContextRef(): ContextRef = {
    val tag = reader.readU8() // Read NODE_CONTEXT tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    ContextRef(loc, pathId)
  }

  private def readStreamletRef(): StreamletRef = {
    val tag = reader.readU8() // Read NODE_STREAMLET tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifier()
    StreamletRef(loc, keyword, pathId)
  }

  private def readInletRef(): InletRef = {
    val tag = reader.readU8() // Read NODE_INLET tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    InletRef(loc, pathId)
  }

  private def readOutletRef(): OutletRef = {
    val tag = reader.readU8() // Read NODE_OUTLET tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    OutletRef(loc, pathId)
  }

  private def readSagaRef(): SagaRef = {
    val tag = reader.readU8() // Read NODE_SAGA tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    SagaRef(loc, pathId)
  }

  private def readUserRef(): UserRef = {
    val nodeType = reader.readU8() // Should be NODE_USER
    val loc = readLocation()
    val pathId = readPathIdentifier()
    UserRef(loc, pathId)
  }

  private def readEpicRef(): EpicRef = {
    val tag = reader.readU8() // Read NODE_EPIC tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    EpicRef(loc, pathId)
  }

  private def readGroupRef(): GroupRef = {
    val tag = reader.readU8() // Read NODE_GROUP tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifier()
    GroupRef(loc, keyword, pathId)
  }

  private def readInputRef(): InputRef = {
    val tag = reader.readU8() // Read NODE_INPUT tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifier()
    InputRef(loc, keyword, pathId)
  }

  private def readOutputRef(): OutputRef = {
    val tag = reader.readU8() // Read NODE_OUTPUT tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifier()
    OutputRef(loc, keyword, pathId)
  }

  private def readDomainRef(): DomainRef = {
    val tag = reader.readU8() // Read NODE_DOMAIN tag
    val loc = readLocation()
    val pathId = readPathIdentifier()
    DomainRef(loc, pathId)
  }

  private def readMessageRef(): MessageRef = {
    val refTag = reader.peekU8()
    refTag match {
      case NODE_TYPE =>
        reader.readU8() // consume tag
        val subtype = reader.readU8() // subtype comes BEFORE location
        val loc = readLocation()
        val pathId = readPathIdentifier()
        subtype match {
          case 0 => CommandRef(loc, pathId)
          case 1 => EventRef(loc, pathId)
          case 2 => QueryRef(loc, pathId)
          case 3 => ResultRef(loc, pathId)
          case 4 => RecordRef(loc, pathId)
          case _ => RecordRef(loc, pathId)
        }
      case _ =>
        // Fallback
        RecordRef(At.empty, PathIdentifier.empty)
    }
  }

  private def readProcessorRef(): ProcessorRef[Processor[?]] = {
    val refTag = reader.peekU8()
    refTag match {
      case NODE_ADAPTOR => readAdaptorRef()
      case NODE_ENTITY => readEntityRef()
      case NODE_REPOSITORY => readRepositoryRef()
      case NODE_PROJECTOR => readProjectorRef()
      case NODE_CONTEXT => readContextRef()
      case NODE_STREAMLET => readStreamletRef()
      case _ =>
        // Fallback
        EntityRef(At.empty, PathIdentifier.empty)
    }
  }

  private def readPortletRef(): PortletRef[Portlet] = {
    val refTag = reader.peekU8()
    refTag match {
      case NODE_INLET => readInletRef()
      case NODE_OUTLET => readOutletRef()
      case _ =>
        // Fallback
        InletRef(At.empty, PathIdentifier.empty)
    }
  }

  // ========== Helper Methods ==========

  private def readLocation(): At = {
    if lastLocation.isEmpty then
      // First location: read full data
      val originPath = readString()
      val offset = reader.readVarInt()
      val line = reader.readVarInt()
      val col = reader.readVarInt()

      // Reconstruct URL from origin path
      // If path starts with / it's absolute, otherwise treat as relative from cwd
      val url = if originPath.isEmpty then URL.empty
                else if originPath.startsWith("/") then URL.fromFullPath(originPath)
                else URL.fromCwdPath(originPath)

      // Create BASTParserInput with synthetic line numbering, passing originPath for origin
      val source = BASTParserInput(url, originPath, 10000)

      // Use createAt to get location with correct line/col
      val loc = source.createAt(line, col)
      lastLocation = loc
      loc
    else
      // Subsequent locations: read deltas
      val sourceFlag = reader.readU8()
      val source = if sourceFlag == 1 then
        val originPath = readString()
        val url = if originPath.isEmpty then URL.empty
                  else if originPath.startsWith("/") then URL.fromFullPath(originPath)
                  else URL.fromCwdPath(originPath)
        BASTParserInput(url, originPath, 10000)
      else
        lastLocation.source.asInstanceOf[BASTParserInput]

      val offsetDelta = reader.readVarInt() - 1000000
      val lineDelta = reader.readVarInt() - 1000
      val colDelta = reader.readVarInt() - 1000

      val line = lastLocation.line + lineDelta
      val col = lastLocation.col + colDelta


      // Use createAt to get location with correct line/col
      val loc = source.createAt(line, col)
      lastLocation = loc
      loc
  }

  private def readIdentifier(): Identifier = {
    val nodeType = reader.readU8() // Should be NODE_IDENTIFIER
    val loc = readLocation()
    val value = readString()
    Identifier(loc, value)
  }

  private def readPathIdentifier(): PathIdentifier = {
    val nodeType = reader.readU8() // Should be NODE_PATH_IDENTIFIER
    val loc = readLocation()
    val value = readSeq(() => readString())
    PathIdentifier(loc, value)
  }

  private def readLiteralString(): LiteralString = {
    val nodeType = reader.readU8() // Should be NODE_LITERAL_STRING
    val loc = readLocation()
    val s = readString()
    LiteralString(loc, s)
  }

  private def readString(): String = {
    val index = reader.readVarInt()
    if index >= stringTable.size then
      throw new IllegalArgumentException(s"Invalid string table index: $index (table size: ${stringTable.size})")
    end if
    stringTable.lookup(index)
  }

  private def readURL(): URL = {
    val urlStr = readString()
    URL(urlStr)
  }

  private def readOption[T](readValue: => T): Option[T] = {
    val hasValue = reader.readBoolean()
    if hasValue then Some(readValue) else None
  }

  private def readSeq[T](readElement: () => T): Seq[T] = {
    val count = reader.readVarInt()
    (0 until count).map(_ => readElement())
  }

  /** Read contents count but defer reading elements
    *
    * Elements are read by the main traversal loop
    */
  private def readContentsDeferred[T <: RiddlValue](): Contents[T] = {
    val count = reader.readVarInt()
    val buffer = ArrayBuffer[T]()

    // Read the actual nodes
    var i = 0
    while i < count do
      val node = readNode().asInstanceOf[T]
      buffer += node
      i += 1
    end while

    Contents(buffer.toSeq: _*)
  }

  private def readMetadataDeferred(): Contents[MetaData] = {
    // Read metadata items - writer writes count + inline items
    readContentsDeferred[MetaData]()
  }

  private def addError(message: String): Unit = {
    messages += Messages.error(message, At.empty)
  }
}
