/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
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
  private var pathTable: PathTable = _  // Phase 8: Path table for path interning
  private var lastLocation: At = At.empty
  private var firstLocationRead: Boolean = false
  private var currentSourcePath: String = ""
  private var currentSource: RiddlParserInput = RiddlParserInput.empty
  private val messages = ArrayBuffer[Messages.Message]()

  /** Phase 7 optimization: Track whether current node has metadata
    *
    * Set by readNode() before dispatching to specific node reader.
    * Node readers check this to know if they should read metadata.
    */
  private var currentNodeHasMetadata: Boolean = false

  // AST context stack for better error messages
  // Tracks what we're currently deserializing (e.g., "Domain(MyDomain) -> Context(MyContext) -> Entity")
  private val contextStack = ArrayBuffer[String]()

  /** Push a context entry when entering a node */
  private def pushContext(nodeType: String, name: String = ""): Unit = {
    val entry = if name.nonEmpty then s"$nodeType($name)" else nodeType
    contextStack += entry
  }

  /** Pop context when leaving a node */
  private def popContext(): Unit = {
    if contextStack.nonEmpty then contextStack.remove(contextStack.length - 1)
  }

  /** Get current context path as string */
  private def contextPath: String = {
    if contextStack.isEmpty then "<root>"
    else contextStack.mkString(" -> ")
  }

  /** Generate a detailed deserialization error message */
  private def deserializationError(
    message: String,
    expectedValue: Option[String] = None,
    actualValue: Option[String] = None
  ): String = {
    val sb = new StringBuilder()
    sb.append(s"BAST deserialization error: $message\n")
    sb.append(s"  Byte position: ${reader.position}\n")
    sb.append(s"  AST context: $contextPath\n")
    expectedValue.foreach(v => sb.append(s"  Expected: $v\n"))
    actualValue.foreach(v => sb.append(s"  Actual: $v\n"))
    sb.append(s"  String table size: ${if stringTable != null then stringTable.size else "not loaded"}\n")
    // Show surrounding bytes for debugging
    val pos = reader.position
    val start = math.max(0, pos - 8)
    val end = math.min(bytes.length, pos + 8)
    val hexBytes = bytes.slice(start, end).map(b => f"${b & 0xFF}%02X").mkString(" ")
    sb.append(s"  Bytes around position [$start-$end]: $hexBytes")
    sb.toString()
  }

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

      // Phase 8: Load path table (immediately follows string table)
      pathTable = PathTable.readFrom(reader, stringTable)

      // Save string table offset for bounds checking
      val stringTableBoundary = header.stringTableOffset

      // Read root Nebula from root offset
      reader.seek(header.rootOffset)
      val nebula = readRootNode(stringTableBoundary)

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
    val version = reader.readInt()
    val flags = reader.readShort()
    reader.readShort() // reserved1
    val stringTableOffset = reader.readInt()
    val rootOffset = reader.readInt()
    val fileSize = reader.readInt()
    val checksum = reader.readInt()
    val reserved = reader.readRawBytes(4)

    BinaryFormat.Header(
      magic = magic,
      version = version,
      flags = flags,
      stringTableOffset = stringTableOffset,
      rootOffset = rootOffset,
      fileSize = fileSize,
      checksum = checksum,
      reserved = reserved
    )
  }

  // ========== Root Node Reading ==========

  // Boundary for node data - beyond this is string table
  private var nodeDataBoundary: Int = Int.MaxValue
  private var lastNodeRead: String = "none"
  private var lastNodePosition: Int = 0

  private def checkBoundary(context: String): Unit = {
    if reader.position > nodeDataBoundary then
      throw new IllegalStateException(
        s"Reader exceeded node boundary at position ${reader.position} (boundary=$nodeDataBoundary) while reading $context. Last successful node: $lastNodeRead at position $lastNodePosition")
    end if
  }

  private def recordNodeRead(nodeName: String, position: Int): Unit = {
    lastNodeRead = nodeName
    lastNodePosition = position
  }

  // Debug flag - set to true to enable verbose position tracking
  private var debugPositionTracking: Boolean = false

  /** Enable debug position tracking for this reader */
  def enableDebugTracking(): Unit = debugPositionTracking = true

  private def debugLog(msg: String): Unit = {
    if debugPositionTracking then println(msg)
  }

  private def readRootNode(boundary: Int): Nebula = {
    nodeDataBoundary = boundary
    var nodeType = reader.readU8()

    // Handle FILE_CHANGE_MARKER at the start
    if nodeType == FILE_CHANGE_MARKER then
      val newPath = readString()
      currentSourcePath = newPath
      val url = if newPath.isEmpty then URL.empty
                else if newPath.startsWith("/") then URL.fromFullPath(newPath)
                else URL.fromCwdPath(newPath)
      val originStr = if newPath.isEmpty then "empty" else newPath
      currentSource = BASTParserInput(url, originStr, 10000)
      // Reset location tracking for new source - first location will be absolute
      lastLocation = At.empty
      firstLocationRead = false
      nodeType = reader.readU8()
    end if

    if nodeType != NODE_NEBULA then
      throw new IllegalArgumentException(s"Expected Nebula root node, got node type $nodeType")
    end if

    val loc = readLocation()
    val _id = readIdentifier() // Nebula has no explicit id, this is empty
    val contents = readContentsDeferred[NebulaContents]()
    // Nebula doesn't have metadata - don't try to read any
    // (currentNodeHasMetadata flag may be set from last content item)

    Nebula(loc, contents)
  }

  // ========== Node Deserialization ==========

  /** Convert node type tag to human-readable name */
  private def nodeTypeName(nodeType: Int): String = nodeType match {
    case NODE_NEBULA => "Nebula"
    case NODE_DOMAIN => "Domain"
    case NODE_CONTEXT => "Context"
    case NODE_ENTITY => "Entity"
    case NODE_MODULE => "Module"
    case NODE_INCLUDE => "Include"
    case NODE_BAST_IMPORT => "BASTImport"
    case NODE_TYPE => "Type"
    case NODE_FIELD => "Field"
    case NODE_ENUMERATOR => "Enumerator"
    case NODE_ADAPTOR => "Adaptor"
    case NODE_FUNCTION => "Function"
    case NODE_PROJECTOR => "Projector"
    case NODE_REPOSITORY => "Repository"
    case NODE_SCHEMA => "Schema"
    case NODE_STREAMLET => "Streamlet"
    case NODE_SAGA => "Saga"
    case NODE_HANDLER => "Handler"
    case NODE_SAGA_STEP => "SagaStep"
    case NODE_STATE => "State"
    case NODE_INVARIANT => "Invariant"
    case NODE_ON_CLAUSE => "OnClause"
    case NODE_INLET => "Inlet"
    case NODE_OUTLET => "Outlet"
    case NODE_CONNECTOR => "Connector"
    case NODE_EPIC => "Epic"
    case NODE_USER => "User"
    case NODE_PIPE => "Pipe"
    case NODE_GROUP => "Group"
    case NODE_INPUT => "Input"
    case NODE_OUTPUT => "Output"
    case NODE_DESCRIPTION => "Description"
    case NODE_BLOCK_DESCRIPTION => "BlockDescription"
    case NODE_COMMENT => "Comment"
    case NODE_BLOCK_COMMENT => "BlockComment"
    case NODE_IDENTIFIER => "Identifier"
    case NODE_PATH_IDENTIFIER => "PathIdentifier"
    case NODE_LITERAL_STRING => "LiteralString"
    case NODE_STATEMENT => "Statement"
    case NODE_AUTHOR => "Author"
    case NODE_TERM => "Term"
    case NODE_COMMAND_REF => "CommandRef"
    case NODE_EVENT_REF => "EventRef"
    case NODE_QUERY_REF => "QueryRef"
    case NODE_RESULT_REF => "ResultRef"
    case NODE_RECORD_REF => "RecordRef"
    case STREAMLET_VOID => "Void"
    case STREAMLET_SOURCE => "Source"
    case STREAMLET_SINK => "Sink"
    case STREAMLET_FLOW => "Flow"
    case STREAMLET_MERGE => "Merge"
    case STREAMLET_SPLIT => "Split"
    case ADAPTOR_INBOUND => "InboundAdaptor"
    case ADAPTOR_OUTBOUND => "OutboundAdaptor"
    // Entity Reference tags (Phase 9)
    case NODE_AUTHOR_REF => "AuthorRef"
    case NODE_TYPE_REF => "TypeRef"
    case NODE_FIELD_REF => "FieldRef"
    case NODE_CONSTANT_REF => "ConstantRef"
    case NODE_ADAPTOR_REF => "AdaptorRef"
    case NODE_FUNCTION_REF => "FunctionRef"
    case NODE_HANDLER_REF => "HandlerRef"
    case NODE_STATE_REF => "StateRef"
    case NODE_ENTITY_REF => "EntityRef"
    case NODE_REPOSITORY_REF => "RepositoryRef"
    case NODE_PROJECTOR_REF => "ProjectorRef"
    case NODE_CONTEXT_REF => "ContextRef"
    case NODE_STREAMLET_REF => "StreamletRef"
    case NODE_INLET_REF => "InletRef"
    case NODE_OUTLET_REF => "OutletRef"
    case NODE_SAGA_REF => "SagaRef"
    case NODE_USER_REF => "UserRef"
    case NODE_EPIC_REF => "EpicRef"
    case NODE_GROUP_REF => "GroupRef"
    case NODE_INPUT_REF => "InputRef"
    case NODE_OUTPUT_REF => "OutputRef"
    case NODE_DOMAIN_REF => "DomainRef"
    case _ => s"Unknown($nodeType)"
  }

  /** Read a RiddlValue node based on its type tag */
  private def readNode(): RiddlValue = {
    val posBeforeNode = reader.position
    checkBoundary(s"readNode at position $posBeforeNode")
    var tagByte = reader.readU8()
    debugLog(f"[DEBUG] readNode at pos $posBeforeNode: tagByte=${tagByte & 0xFF}%d (0x${tagByte & 0xFF}%02X)")

    // Check for FILE_CHANGE_MARKER - indicates source file transition
    if tagByte == FILE_CHANGE_MARKER then
      val newPath = readString()
      debugLog(f"[DEBUG] FILE_CHANGE_MARKER: path='$newPath', pos after path=${reader.position}")
      currentSourcePath = newPath
      // Reconstruct URL and create BASTParserInput
      // Empty path means synthetic/test locations - still need a BASTParserInput
      val url = if newPath.isEmpty then URL.empty
                else if newPath.startsWith("/") then URL.fromFullPath(newPath)
                else URL.fromCwdPath(newPath)
      val originStr = if newPath.isEmpty then "empty" else newPath
      currentSource = BASTParserInput(url, originStr, 10000)
      // Reset location tracking for new source - first location will be absolute
      lastLocation = At.empty
      firstLocationRead = false
      // Now read the actual node tag
      tagByte = reader.readU8()
      debugLog(f"[DEBUG] after FILE_CHANGE_MARKER: actual tagByte=${tagByte & 0xFF}%d (0x${tagByte & 0xFF}%02X)")
    end if

    // Phase 7 optimization: Extract metadata flag from high bit
    currentNodeHasMetadata = (tagByte & HAS_METADATA_FLAG) != 0
    val nodeType = (tagByte & 0x7F).toByte

    val nodeName = nodeTypeName(nodeType)
    pushContext(nodeName)

    try {
      val result: RiddlValue = nodeType match {
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
        case NODE_HANDLER => readHandlerNode()
        case NODE_STATEMENT => readStatementNode()
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

        // Message References (dedicated tags)
        case NODE_COMMAND_REF => readCommandRefNode()
        case NODE_EVENT_REF => readEventRefNode()
        case NODE_QUERY_REF => readQueryRefNode()
        case NODE_RESULT_REF => readResultRefNode()
        case NODE_RECORD_REF => readRecordRefNode()

        // Entity References (dedicated tags - Phase 9 fix)
        case NODE_AUTHOR_REF => readAuthorRefNode()
        case NODE_TYPE_REF => readTypeRefNode()
        case NODE_FIELD_REF => readFieldRefNode()
        case NODE_CONSTANT_REF => readConstantRefNode()
        case NODE_ADAPTOR_REF => readAdaptorRefNode()
        case NODE_FUNCTION_REF => readFunctionRefNode()
        case NODE_HANDLER_REF => readHandlerRefNode()
        case NODE_STATE_REF => readStateRefNode()
        case NODE_ENTITY_REF => readEntityRefNode()
        case NODE_REPOSITORY_REF => readRepositoryRefNode()
        case NODE_PROJECTOR_REF => readProjectorRefNode()
        case NODE_CONTEXT_REF => readContextRefNode()
        case NODE_STREAMLET_REF => readStreamletRefNode()
        case NODE_INLET_REF => readInletRefNode()
        case NODE_OUTLET_REF => readOutletRefNode()
        case NODE_SAGA_REF => readSagaRefNode()
        case NODE_USER_REF => readUserRefNode()
        case NODE_EPIC_REF => readEpicRefNode()
        case NODE_GROUP_REF => readGroupRefNode()
        case NODE_INPUT_REF => readInputRefNode()
        case NODE_OUTPUT_REF => readOutputRefNode()
        case NODE_DOMAIN_REF => readDomainRefNode()

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
          addError(deserializationError(
            s"Unknown node type tag",
            expectedValue = Some("valid node type (1-255)"),
            actualValue = Some(s"$nodeType at byte $posBeforeNode")
          ))
          // Return a placeholder with lastLocation for best-effort location on error
          LiteralString(lastLocation, s"<unknown node type $nodeType>")
      }
      popContext()
      val posAfterNode = reader.position
      recordNodeRead(nodeName, posBeforeNode)
      debugLog(f"[DEBUG] finished $nodeName at pos $posAfterNode (read ${posAfterNode - posBeforeNode} bytes)")
      result
    } catch {
      case e: Exception =>
        popContext()
        throw e // Re-throw with context already in error message
    }
  }

  // ========== Container Nodes ==========

  private def readNebulaNode(): Nebula = {
    val loc = readLocation()
    val _id = readIdentifier()
    val contents = readContentsDeferred[NebulaContents]()
    // Nebula doesn't have metadata - don't try to read any
    Nebula(loc, contents)
  }

  private def readDomainNode(): Domain = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents (children overwrite flag)
    debugLog(f"[DEBUG] readDomainNode: hasMetadata=$hasMetadata at pos ${reader.position}")
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    debugLog(f"[DEBUG] readDomainNode: domain '${id.value}' at pos ${reader.position}")
    val contents = readContentsDeferred[OccursInDomain]().asInstanceOf[Contents[DomainContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    debugLog(f"[DEBUG] readDomainNode: reading metadata (hasMetadata=$hasMetadata) at pos ${reader.position}")
    val metadata = readMetadataDeferred()
    debugLog(f"[DEBUG] readDomainNode: finished, metadata count=${metadata.length}")
    Domain(loc, id, contents, metadata)
  }

  private def readContextNode(): Context = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[OccursInContext]().asInstanceOf[Contents[ContextContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Context(loc, id, contents, metadata)
  }

  private def readEntityNode(): Entity = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[OccursInProcessor | State]().asInstanceOf[Contents[EntityContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Entity(loc, id, contents, metadata)
  }

  private def readModuleNode(): Module = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[Domain | Author | Comment]().asInstanceOf[Contents[ModuleContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
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
    // Read selective import fields
    val kind = readOption(readString())
    val selector = readOption(readIdentifierInline())
    val alias = readOption(readIdentifierInline())
    // Contents are not stored in BAST - they're loaded dynamically
    // by BASTLoader when this import is encountered
    BASTImport(loc, path, kind, selector, alias)
  }

  // ========== Type Definitions ==========

  private def readTypeNode(): Type = {
    val hasMetadata = currentNodeHasMetadata  // Save before reading nested content
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val typEx = readTypeExpression()
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Type(loc, id, typEx, metadata)
  }

  private def readFieldOrConstantOrMethod(): RiddlValue = {
    val hasMetadata = currentNodeHasMetadata  // Save before reading nested content
    val loc = readLocation()
    val idOrName = reader.peekU8()

    // Check if next is NODE_IDENTIFIER or a string
    if idOrName == NODE_IDENTIFIER then
      val id = readIdentifier()
      val typEx = readTypeExpression()

      // Check if this is a Constant (has value) or Method (has args) or Field
      // This is ambiguous - we need to peek ahead
      // For now, assume Field. Writer should disambiguate better.
      currentNodeHasMetadata = hasMetadata  // Restore for metadata read
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
    val id = readIdentifierInline()  // Inline - no tag
    val enumVal = readOption(reader.readVarLong())
    val metadata = readMetadataDeferred()
    Enumerator(loc, id, enumVal, metadata)
  }

  // ========== Processor Definitions ==========

  private def readAdaptorNode(): Adaptor = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val directionTag = reader.readU8()
    val direction: AdaptorDirection = directionTag match {
      case ADAPTOR_INBOUND => InboundAdaptor(loc)
      case ADAPTOR_OUTBOUND => OutboundAdaptor(loc)
      case _ => InboundAdaptor(loc) // Default
    }
    val referent = readContextRef()
    val contents = readContentsDeferred[OccursInProcessor]().asInstanceOf[Contents[AdaptorContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Adaptor(loc, id, direction, referent, contents, metadata)
  }

  private def readFunctionNode(): Function = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    // Debug: Read input with type checking
    val inputRaw = readOption(readTypeExpression())
    val input = inputRaw.map { te =>
      te match {
        case agg: Aggregation => agg
        case other =>
          throw new RuntimeException(s"Function ${id.value} input expected Aggregation but got ${other.getClass.getSimpleName} at byte pos ${reader.position}")
      }
    }
    // Debug: Read output with type checking
    val outputRaw = readOption(readTypeExpression())
    val output = outputRaw.map { te =>
      te match {
        case agg: Aggregation => agg
        case other =>
          throw new RuntimeException(s"Function ${id.value} output expected Aggregation but got ${other.getClass.getSimpleName} at byte pos ${reader.position}")
      }
    }
    val contents = readContentsDeferred[OccursInVitalDefinition | Statement | Function]().asInstanceOf[Contents[FunctionContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Function(loc, id, input, output, contents, metadata)
  }

  private def readSagaNode(): Saga = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    // Debug: Read input with type checking
    val inputRaw = readOption(readTypeExpression())
    val input = inputRaw.map { te =>
      te match {
        case agg: Aggregation => agg
        case other =>
          throw new RuntimeException(s"Saga ${id.value} input expected Aggregation but got ${other.getClass.getSimpleName} at byte pos ${reader.position}")
      }
    }
    // Debug: Read output with type checking
    val outputRaw = readOption(readTypeExpression())
    val output = outputRaw.map { te =>
      te match {
        case agg: Aggregation => agg
        case other =>
          throw new RuntimeException(s"Saga ${id.value} output expected Aggregation but got ${other.getClass.getSimpleName} at byte pos ${reader.position}")
      }
    }
    val contents = readContentsDeferred[OccursInVitalDefinition | SagaStep]().asInstanceOf[Contents[SagaContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Saga(loc, id, input, output, contents, metadata)
  }

  private def readProjectorNode(): Projector = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[OccursInProcessor | RepositoryRef]().asInstanceOf[Contents[ProjectorContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Projector(loc, id, contents, metadata)
  }

  private def readRepositoryNode(): Repository = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[OccursInProcessor | Schema]().asInstanceOf[Contents[RepositoryContents]]
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Repository(loc, id, contents, metadata)
  }

  private def readSchemaNode(): Schema = {
    // Read schemaKind subtype: 0=Relational, 1=Document, 2=Graphical
    val subtype = reader.readU8()
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag

    val schemaKind = subtype match {
      case 0 => RepositorySchemaKind.Relational
      case 1 => RepositorySchemaKind.Document
      case 2 => RepositorySchemaKind.Graphical
      case _ => RepositorySchemaKind.Relational
    }

    // Read data map - keys use writeIdentifier (with tag)
    val dataCount = reader.readVarInt()
    val data = (0 until dataCount).map { _ =>
      val dataId = readIdentifier()
      val tref = readTypeRef()
      (dataId, tref)
    }.toMap

    // Read links map - keys use writeIdentifier (with tag)
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
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
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
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Streamlet(loc, id, shape, contents, metadata)
  }

  // ========== Epic Definitions ==========

  private def readEpicOrUseCaseNode(): RiddlValue = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag

    // Check if next is UserStory (NODE_USER tag) or Contents
    val saved = reader.position
    val nextTag = reader.readU8()
    reader.seek(saved)

    if nextTag == NODE_USER then
      val userStory = readUserStoryNode()
      val contents = readContentsDeferred[OccursInVitalDefinition | ShownBy | UseCase]().asInstanceOf[Contents[EpicContents]]
      currentNodeHasMetadata = hasMetadata  // Restore for metadata read
      val metadata = readMetadataDeferred()
      Epic(loc, id, userStory, contents, metadata)
    else
      // UseCase
      val userStory = readUserStoryNode()
      val contents = readContentsDeferred[UseCaseContents]()
      currentNodeHasMetadata = hasMetadata  // Restore for metadata read
      val metadata = readMetadataDeferred()
      UseCase(loc, id, userStory, contents, metadata)
    end if
  }

  // ========== Handler Components ==========

  /** Read a Handler node (Phase 7: handlers have dedicated NODE_HANDLER tag) */
  private def readHandlerNode(): Handler = {
    val hasMetadata = currentNodeHasMetadata  // Save before contents
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val contents = readContentsDeferred[HandlerContents]()
    currentNodeHasMetadata = hasMetadata  // Restore for metadata read
    val metadata = readMetadataDeferred()
    Handler(loc, id, contents, metadata)
  }

  /** Read a Statement node (Phase 7: statements have dedicated NODE_STATEMENT tag) */
  private def readStatementNode(): Statement = {
    val stmtType = reader.readU8()
    val loc = readLocation()
    readStatement(loc, stmtType)
  }

  private def readSagaStepNode(): SagaStep = {
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val doStatements = readContentsDeferred[Statements]()
    val undoStatements = readContentsDeferred[Statements]()
    val metadata = readMetadataDeferred()
    SagaStep(loc, id, doStatements, undoStatements, metadata)
  }

  private def readStatement(loc: At, stmtType: Int): Statement = {
    stmtType match {
      case 0 => // Prompt
        val what = readLiteralString()
        PromptStatement(loc, what)

      case 1 => // Error
        val message = readLiteralString()
        ErrorStatement(loc, message)

      case 3 => // Set
        val field = readFieldRef()
        val value = readLiteralString()
        SetStatement(loc, field, value)

      case 5 => // Send
        val msg = readMessageRef()
        val portlet = readPortletRef()
        SendStatement(loc, msg, portlet)

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

      case 10 => // When
        val conditionType = reader.readU8()
        val condition: LiteralString | Identifier = conditionType match {
          case 0 => readLiteralString()
          case 1 => readIdentifierInline()
          case _ => throw new RuntimeException(s"Invalid when condition type: $conditionType")
        }
        val negated = reader.readU8() != 0
        val thenStatements = readContentsDeferred[Statements]()
        val elseStatements = readContentsDeferred[Statements]()
        WhenStatement(loc, condition, thenStatements, elseStatements, negated)

      case 11 => // Match
        val expression = readLiteralString()
        val numCases = reader.readVarInt()
        val cases = (0 until numCases).map { _ =>
          val caseLoc = readLocation()
          val pattern = readLiteralString()
          val statements = readContentsDeferred[Statements]()
          MatchCase(caseLoc, pattern, statements)
        }.toSeq
        val default = readContentsDeferred[Statements]()
        MatchStatement(loc, expression, cases, default)

      case 12 => // Let
        val identifier = readIdentifier()
        val expression = readLiteralString()
        LetStatement(loc, identifier, expression)

      case 13 => // Code
        val language = readLiteralString()
        val body = readString()
        CodeStatement(loc, language, body)

      case _ =>
        PromptStatement(loc, LiteralString(loc, s"<unknown statement $stmtType>"))
    }
  }

  private def readStateNode(): State = {
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
    val typ = readTypeRefInline()    // Inline - position known
    val metadata = readMetadataDeferred()
    State(loc, id, typ, metadata)
  }

  private def readInvariantNode(): Invariant = {
    val loc = readLocation()
    val id = readIdentifierInline()  // Inline - no tag
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
    val id = readIdentifierInline()  // Inline - no tag
    val type_ = readTypeRefInline()  // Inline - position known
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
      val type_ = readTypeRefInline()  // Inline - position known
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
    val id = readIdentifierInline()  // Inline - no tag
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
        val id = readIdentifierInline()  // Inline - no tag
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
    val id = readIdentifierInline()  // Inline - no tag
    val verbAlias = readString()
    val takeIn = readTypeRefInline()  // Inline - position known
    val contents = readContentsDeferred[OccursInInput]()
    val metadata = readMetadataDeferred()
    Input(loc, nounAlias, id, verbAlias, takeIn, contents, metadata)
  }

  private def readOutputNode(): Output = {
    val loc = readLocation()
    val nounAlias = readString()
    val id = readIdentifierInline()  // Inline - no tag
    val verbAlias = readString()

    // Read union type
    val putOutType = reader.readU8()
    val putOut: TypeRef | ConstantRef | LiteralString = putOutType match {
      case 0 => readTypeRefInline()  // Inline - discriminator identifies type
      case 1 => readConstantRef()
      case 2 => readLiteralString()
      case _ => readTypeRefInline()  // Inline - discriminator identifies type
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
      val pathId = readPathIdentifierInline()
      AuthorRef(loc, pathId)
    else
      // Author
      val id = readIdentifierInline()  // Inline - no tag
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
      val pathId = readPathIdentifierInline()
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
    val id = readIdentifierInline()  // Inline - no tag
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
        // Alternation items are AliasedTypeExpression which are written as TYPE_REF
        // We must use readTypeExpression() not readNode() since TYPE_REF is a type tag, not a node tag
        val of = readTypeExpressionContents()
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
            val pathId = readPathIdentifierInline()
            AliasedTypeExpression(loc, keyword, pathId)

          case 10 => // EntityReference
            val entity = readPathIdentifierInline()
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
            val entityPath = readPathIdentifierInline()
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

      // Phase 7 optimization: Predefined type tags
      case TYPE_INTEGER =>
        val loc = readLocation()
        Integer(loc)

      case TYPE_NATURAL =>
        val loc = readLocation()
        Natural(loc)

      case TYPE_WHOLE =>
        val loc = readLocation()
        Whole(loc)

      case TYPE_REAL =>
        val loc = readLocation()
        Real(loc)

      case TYPE_STRING_DEFAULT =>
        val loc = readLocation()
        String_(loc, None, None)

      case TYPE_UUID =>
        val loc = readLocation()
        UUID(loc)

      case TYPE_DATE =>
        val loc = readLocation()
        Date(loc)

      case TYPE_TIME =>
        val loc = readLocation()
        Time(loc)

      case TYPE_DATETIME =>
        val loc = readLocation()
        DateTime(loc)

      case TYPE_TIMESTAMP =>
        val loc = readLocation()
        TimeStamp(loc)

      case TYPE_DURATION =>
        val loc = readLocation()
        Duration(loc)

      case _ =>
        addError(s"Unknown type expression tag: $typeTag at position ${reader.position}")
        // Use lastLocation for best-effort location on error
        Abstract(lastLocation)
    }
  }

  // ========== References ==========

  private def readReference(): Reference[Definition] = {
    // Peek at tag to determine type - each reader consumes its own tag
    val refTag = reader.peekU8()

    refTag match {
      // Legacy definition tags used as refs (for backward compatibility)
      case NODE_AUTHOR => readAuthorRef()
      case NODE_TYPE => readTypeRef()
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
      // Message References (dedicated tags)
      case NODE_COMMAND_REF =>
        reader.readU8()  // consume tag
        readCommandRefNode()
      case NODE_EVENT_REF =>
        reader.readU8()  // consume tag
        readEventRefNode()
      case NODE_QUERY_REF =>
        reader.readU8()  // consume tag
        readQueryRefNode()
      case NODE_RESULT_REF =>
        reader.readU8()  // consume tag
        readResultRefNode()
      case NODE_RECORD_REF =>
        reader.readU8()  // consume tag
        readRecordRefNode()
      // Entity References (dedicated tags - Phase 9)
      case NODE_AUTHOR_REF =>
        reader.readU8()  // consume tag
        readAuthorRefNode()
      case NODE_TYPE_REF =>
        reader.readU8()  // consume tag
        readTypeRefNode()
      case NODE_FIELD_REF =>
        reader.readU8()  // consume tag
        readFieldRefNode()
      case NODE_CONSTANT_REF =>
        reader.readU8()  // consume tag
        readConstantRefNode()
      case NODE_ADAPTOR_REF =>
        reader.readU8()  // consume tag
        readAdaptorRefNode()
      case NODE_FUNCTION_REF =>
        reader.readU8()  // consume tag
        readFunctionRefNode()
      case NODE_HANDLER_REF =>
        reader.readU8()  // consume tag
        readHandlerRefNode()
      case NODE_STATE_REF =>
        reader.readU8()  // consume tag
        readStateRefNode()
      case NODE_ENTITY_REF =>
        reader.readU8()  // consume tag
        readEntityRefNode()
      case NODE_REPOSITORY_REF =>
        reader.readU8()  // consume tag
        readRepositoryRefNode()
      case NODE_PROJECTOR_REF =>
        reader.readU8()  // consume tag
        readProjectorRefNode()
      case NODE_CONTEXT_REF =>
        reader.readU8()  // consume tag
        readContextRefNode()
      case NODE_STREAMLET_REF =>
        reader.readU8()  // consume tag
        readStreamletRefNode()
      case NODE_INLET_REF =>
        reader.readU8()  // consume tag
        readInletRefNode()
      case NODE_OUTLET_REF =>
        reader.readU8()  // consume tag
        readOutletRefNode()
      case NODE_SAGA_REF =>
        reader.readU8()  // consume tag
        readSagaRefNode()
      case NODE_USER_REF =>
        reader.readU8()  // consume tag
        readUserRefNode()
      case NODE_EPIC_REF =>
        reader.readU8()  // consume tag
        readEpicRefNode()
      case NODE_GROUP_REF =>
        reader.readU8()  // consume tag
        readGroupRefNode()
      case NODE_INPUT_REF =>
        reader.readU8()  // consume tag
        readInputRefNode()
      case NODE_OUTPUT_REF =>
        reader.readU8()  // consume tag
        readOutputRefNode()
      case NODE_DOMAIN_REF =>
        reader.readU8()  // consume tag
        readDomainRefNode()
      case _ =>
        reader.readU8() // consume the unknown tag
        addError(s"Unknown reference tag: $refTag")
        // Use lastLocation for best-effort location on error
        TypeRef(lastLocation, "", PathIdentifier(lastLocation, Seq.empty))
    }
  }

  private def readAuthorRef(): AuthorRef = {
    val tag = reader.readU8() // Read NODE_AUTHOR tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    AuthorRef(loc, pathId)
  }

  private def readTypeRef(): TypeRef = {
    val tag = reader.readU8() // Read NODE_TYPE tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    TypeRef(loc, keyword, pathId)
  }

  /** Read TypeRef without tag - used when type is known from position */
  private def readTypeRefInline(): TypeRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    TypeRef(loc, keyword, pathId)
  }

  private def readFieldRefOrConstantRef(): FieldRef | ConstantRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    // For now, default to FieldRef
    FieldRef(loc, pathId)
  }

  private def readFieldRef(): FieldRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    FieldRef(loc, pathId)
  }

  private def readConstantRef(): ConstantRef = {
    val tag = reader.readU8() // Read NODE_FIELD tag (constants use same tag as fields)
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ConstantRef(loc, pathId)
  }

  // Message Ref Node readers - called from readNode() dispatch where tag is already consumed
  // They do NOT consume the tag - just read location and pathId
  private def readCommandRefNode(): CommandRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    CommandRef(loc, pathId)
  }

  private def readEventRefNode(): EventRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    EventRef(loc, pathId)
  }

  private def readQueryRefNode(): QueryRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    QueryRef(loc, pathId)
  }

  private def readResultRefNode(): ResultRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ResultRef(loc, pathId)
  }

  private def readRecordRefNode(): RecordRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    RecordRef(loc, pathId)
  }

  private def readAdaptorRef(): AdaptorRef = {
    val tag = reader.readU8() // Read NODE_ADAPTOR tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    AdaptorRef(loc, pathId)
  }

  private def readFunctionRef(): FunctionRef = {
    val tag = reader.readU8() // Read NODE_FUNCTION tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    FunctionRef(loc, pathId)
  }

  private def readHandlerRef(): HandlerRef = {
    val tag = reader.readU8() // Read NODE_HANDLER tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    HandlerRef(loc, pathId)
  }

  private def readStateRef(): StateRef = {
    val tag = reader.readU8() // Read NODE_STATE tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    StateRef(loc, pathId)
  }

  private def readEntityRef(): EntityRef = {
    val tag = reader.readU8() // Read NODE_ENTITY tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    EntityRef(loc, pathId)
  }

  private def readRepositoryRef(): RepositoryRef = {
    val tag = reader.readU8() // Read NODE_REPOSITORY tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    RepositoryRef(loc, pathId)
  }

  private def readProjectorRef(): ProjectorRef = {
    val tag = reader.readU8() // Read NODE_PROJECTOR tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ProjectorRef(loc, pathId)
  }

  private def readContextRef(): ContextRef = {
    val tag = reader.readU8() // Read NODE_CONTEXT tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ContextRef(loc, pathId)
  }

  private def readStreamletRef(): StreamletRef = {
    val tag = reader.readU8() // Read NODE_STREAMLET tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    StreamletRef(loc, keyword, pathId)
  }

  private def readInletRef(): InletRef = {
    val tag = reader.readU8() // Read NODE_INLET tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    InletRef(loc, pathId)
  }

  private def readOutletRef(): OutletRef = {
    val tag = reader.readU8() // Read NODE_OUTLET tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    OutletRef(loc, pathId)
  }

  private def readSagaRef(): SagaRef = {
    val tag = reader.readU8() // Read NODE_SAGA tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    SagaRef(loc, pathId)
  }

  private def readUserRef(): UserRef = {
    val nodeType = reader.readU8() // Should be NODE_USER
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    UserRef(loc, pathId)
  }

  private def readEpicRef(): EpicRef = {
    val tag = reader.readU8() // Read NODE_EPIC tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    EpicRef(loc, pathId)
  }

  private def readGroupRef(): GroupRef = {
    val tag = reader.readU8() // Read NODE_GROUP tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    GroupRef(loc, keyword, pathId)
  }

  private def readInputRef(): InputRef = {
    val tag = reader.readU8() // Read NODE_INPUT tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    InputRef(loc, keyword, pathId)
  }

  private def readOutputRef(): OutputRef = {
    val tag = reader.readU8() // Read NODE_OUTPUT tag
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    OutputRef(loc, keyword, pathId)
  }

  private def readDomainRef(): DomainRef = {
    val tag = reader.readU8() // Read NODE_DOMAIN tag
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    DomainRef(loc, pathId)
  }

  // ========== Entity Reference Node Readers (Phase 9 fix) ==========
  // These are called from readNode() dispatch where tag is already consumed.
  // They do NOT consume the tag - just read location and pathId.

  private def readAuthorRefNode(): AuthorRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    AuthorRef(loc, pathId)
  }

  private def readTypeRefNode(): TypeRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    TypeRef(loc, keyword, pathId)
  }

  private def readFieldRefNode(): FieldRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    FieldRef(loc, pathId)
  }

  private def readConstantRefNode(): ConstantRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ConstantRef(loc, pathId)
  }

  private def readAdaptorRefNode(): AdaptorRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    AdaptorRef(loc, pathId)
  }

  private def readFunctionRefNode(): FunctionRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    FunctionRef(loc, pathId)
  }

  private def readHandlerRefNode(): HandlerRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    HandlerRef(loc, pathId)
  }

  private def readStateRefNode(): StateRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    StateRef(loc, pathId)
  }

  private def readEntityRefNode(): EntityRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    EntityRef(loc, pathId)
  }

  private def readRepositoryRefNode(): RepositoryRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    RepositoryRef(loc, pathId)
  }

  private def readProjectorRefNode(): ProjectorRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ProjectorRef(loc, pathId)
  }

  private def readContextRefNode(): ContextRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    ContextRef(loc, pathId)
  }

  private def readStreamletRefNode(): StreamletRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    StreamletRef(loc, keyword, pathId)
  }

  private def readInletRefNode(): InletRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    InletRef(loc, pathId)
  }

  private def readOutletRefNode(): OutletRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    OutletRef(loc, pathId)
  }

  private def readSagaRefNode(): SagaRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    SagaRef(loc, pathId)
  }

  private def readUserRefNode(): UserRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    UserRef(loc, pathId)
  }

  private def readEpicRefNode(): EpicRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    EpicRef(loc, pathId)
  }

  private def readGroupRefNode(): GroupRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    GroupRef(loc, keyword, pathId)
  }

  private def readInputRefNode(): InputRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    InputRef(loc, keyword, pathId)
  }

  private def readOutputRefNode(): OutputRef = {
    val loc = readLocation()
    val keyword = readString()
    val pathId = readPathIdentifierInline()
    OutputRef(loc, keyword, pathId)
  }

  private def readDomainRefNode(): DomainRef = {
    val loc = readLocation()
    val pathId = readPathIdentifierInline()
    DomainRef(loc, pathId)
  }

  private def readMessageRef(): MessageRef = {
    val refTag = reader.peekU8()
    refTag match {
      case NODE_COMMAND_REF =>
        reader.readU8()  // consume tag
        readCommandRefNode()
      case NODE_EVENT_REF =>
        reader.readU8()  // consume tag
        readEventRefNode()
      case NODE_QUERY_REF =>
        reader.readU8()  // consume tag
        readQueryRefNode()
      case NODE_RESULT_REF =>
        reader.readU8()  // consume tag
        readResultRefNode()
      case NODE_RECORD_REF =>
        reader.readU8()  // consume tag
        readRecordRefNode()
      case _ =>
        addError(s"Unknown message ref tag: $refTag")
        // Use lastLocation for best-effort location on error
        RecordRef(lastLocation, PathIdentifier(lastLocation, Seq.empty))
    }
  }

  private def readProcessorRef(): ProcessorRef[Processor[?]] = {
    val refTag = reader.peekU8()
    refTag match {
      // Legacy definition tags used as refs
      case NODE_ADAPTOR => readAdaptorRef()
      case NODE_ENTITY => readEntityRef()
      case NODE_REPOSITORY => readRepositoryRef()
      case NODE_PROJECTOR => readProjectorRef()
      case NODE_CONTEXT => readContextRef()
      case NODE_STREAMLET => readStreamletRef()
      // New dedicated REF tags (Phase 9)
      case NODE_ADAPTOR_REF =>
        reader.readU8()  // consume tag
        readAdaptorRefNode()
      case NODE_ENTITY_REF =>
        reader.readU8()  // consume tag
        readEntityRefNode()
      case NODE_REPOSITORY_REF =>
        reader.readU8()  // consume tag
        readRepositoryRefNode()
      case NODE_PROJECTOR_REF =>
        reader.readU8()  // consume tag
        readProjectorRefNode()
      case NODE_CONTEXT_REF =>
        reader.readU8()  // consume tag
        readContextRefNode()
      case NODE_STREAMLET_REF =>
        reader.readU8()  // consume tag
        readStreamletRefNode()
      case _ =>
        // Fallback - use lastLocation for best-effort location on error
        EntityRef(lastLocation, PathIdentifier(lastLocation, Seq.empty))
    }
  }

  private def readPortletRef(): PortletRef[Portlet] = {
    val refTag = reader.peekU8()
    refTag match {
      // Legacy definition tags used as refs
      case NODE_INLET => readInletRef()
      case NODE_OUTLET => readOutletRef()
      // New dedicated REF tags (Phase 9)
      case NODE_INLET_REF =>
        reader.readU8()  // consume tag
        readInletRefNode()
      case NODE_OUTLET_REF =>
        reader.readU8()  // consume tag
        readOutletRefNode()
      case _ =>
        // Fallback - use lastLocation for best-effort location on error
        InletRef(lastLocation, PathIdentifier(lastLocation, Seq.empty))
    }
  }

  // ========== Helper Methods ==========

  private def readLocation(): At = {
    // Optimized location format (Phase 7):
    // - Source file changes are handled by FILE_CHANGE_MARKER before node tags
    // - Locations just store offset deltas (no flag byte)
    // - Uses zigzag encoding for signed deltas
    val source = currentSource.asInstanceOf[BASTParserInput]

    if !firstLocationRead then
      // First location: read absolute offsets
      val offset = reader.readVarInt()
      val endOffset = reader.readVarInt()

      // Create At directly from offsets
      val loc = source.createAtFromOffsets(offset, endOffset)
      lastLocation = loc
      firstLocationRead = true
      loc
    else
      // Subsequent locations: read deltas with zigzag decoding
      val offsetDelta = reader.readZigzagInt()
      val endOffsetDelta = reader.readZigzagInt()

      val offset = lastLocation.offset + offsetDelta
      val endOffset = lastLocation.endOffset + endOffsetDelta

      val loc = source.createAtFromOffsets(offset, endOffset)
      lastLocation = loc
      loc
  }

  private def readIdentifier(): Identifier = {
    val nodeType = reader.readU8() // Should be NODE_IDENTIFIER
    val loc = readLocation()
    val value = readString()
    Identifier(loc, value)
  }

  /** Read identifier without tag - used when identifier position is known */
  private def readIdentifierInline(): Identifier = {
    val loc = readLocation()
    val value = readString()
    Identifier(loc, value)
  }

  /** Read PathIdentifier without tag - position is always known within references
    *
    * Phase 8 optimization: Uses path table interning for repeated paths.
    * Encoding:
    * - If count > 0: inline path (read count string indices)
    * - If count == 0: next varint is path table index
    */
  private def readPathIdentifierInline(): PathIdentifier = {
    val loc = readLocation()
    val count = reader.readVarInt()

    val value =
      if count == 0 then
        // Path table lookup
        val pathIndex = reader.readVarInt()
        pathTable.lookup(pathIndex)
      else
        // Inline path - read count string indices
        (0 until count).map(_ => readString())
      end if

    PathIdentifier(loc, value)
  }

  private def readLiteralString(): LiteralString = {
    val nodeType = reader.readU8() // Should be NODE_LITERAL_STRING
    val loc = readLocation()
    val s = readString()
    LiteralString(loc, s)
  }

  private def readString(): String = {
    val posBeforeRead = reader.position
    val index = reader.readVarInt()
    if index >= stringTable.size then
      throw new IllegalArgumentException(
        deserializationError(
          s"Invalid string table index",
          expectedValue = Some(s"index < ${stringTable.size}"),
          actualValue = Some(s"index = $index (read at byte $posBeforeRead)")
        ))
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

  /** Read type expressions as contents
    *
    * Used for Alternation which contains AliasedTypeExpression items.
    * These are written with TYPE_REF tags which readTypeExpression() handles,
    * but readNode() does not handle type expression tags.
    */
  private def readTypeExpressionContents(): Contents[AliasedTypeExpression] = {
    val count = reader.readVarInt()
    val buffer = ArrayBuffer[AliasedTypeExpression]()

    var i = 0
    while i < count do
      val typeExp = readTypeExpression()
      // Alternation items are always AliasedTypeExpression
      typeExp match {
        case ate: AliasedTypeExpression =>
          buffer += ate
        case other =>
          // If we get something else, wrap it in an error and continue
          addError(s"Expected AliasedTypeExpression in Alternation, got ${other.getClass.getSimpleName}")
          buffer += AliasedTypeExpression(other.loc, "", PathIdentifier.empty)
      }
      i += 1
    end while

    Contents(buffer.toSeq: _*)
  }

  /** Read contents count but defer reading elements
    *
    * Elements are read by the main traversal loop
    */
  private def readContentsDeferred[T <: RiddlValue](): Contents[T] = {
    val countPos = reader.position
    val count = reader.readVarInt()
    debugLog(f"[DEBUG] readContentsDeferred at pos $countPos: count=$count")
    pushContext(s"contents[$count]")
    val buffer = ArrayBuffer[T]()

    // Read the actual nodes
    var i = 0
    while i < count do
      debugLog(f"[DEBUG]   reading item ${i + 1} of $count at pos ${reader.position}")
      val node = readNode().asInstanceOf[T]
      debugLog(f"[DEBUG]   item ${i + 1}: ${node.getClass.getSimpleName}")
      buffer += node
      i += 1
    end while

    debugLog(f"[DEBUG] readContentsDeferred finished at pos ${reader.position} (read $count items)")
    popContext()
    Contents(buffer.toSeq: _*)
  }

  private def readMetadataDeferred(): Contents[MetaData] = {
    // Phase 7 optimization: Only read metadata if flag was set
    if !currentNodeHasMetadata then
      return Contents.empty[MetaData]()
    end if

    val countPos = reader.position
    val count = reader.readVarInt()
    pushContext(s"metadata[$count]")
    val buffer = ArrayBuffer[MetaData]()

    var i = 0
    while i < count do
      val node = readNode().asInstanceOf[MetaData]
      buffer += node
      i += 1
    end while

    popContext()
    Contents(buffer.toSeq: _*)
  }

  private def addError(message: String): Unit = {
    messages += Messages.error(message, At.empty)
  }
}
