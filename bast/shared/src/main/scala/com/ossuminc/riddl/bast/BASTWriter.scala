/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{PlatformContext, URL}
import com.ossuminc.riddl.bast.{given, *}
import wvlet.airframe.ulid.ULID

/** Output from BASTWriter pass
  *
  * @param root The root of the AST (unchanged)
  * @param messages Any messages generated during serialization
  * @param bytes The serialized BAST bytes
  * @param nodeCount Total number of nodes serialized
  * @param stringTableSize Number of strings in string table
  */
case class BASTOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  bytes: Array[Byte] = Array.empty,
  nodeCount: Int = 0,
  stringTableSize: Int = 0
) extends PassOutput

object BASTWriter extends PassInfo[PassOptions] {
  val name: String = "BASTWriter"

  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => BASTWriter(in, out)
  }
}

/** BAST serialization pass
  *
  * Converts a RIDDL AST to Binary AST (BAST) format for efficient storage
  * and fast loading. Uses string interning and variable-length encoding to
  * minimize file size.
  *
  * This implementation provides complete serialization for all 169 AST node types
  * to enable reflective serialization: parse RIDDL → AST → BAST → AST → RIDDL
  * should produce identical results.
  *
  * @param input The AST to serialize
  * @param outputs Output from previous passes
  */
case class BASTWriter(input: PassInput, outputs: PassesOutput)(using pc: PlatformContext)
    extends Pass(input, outputs, withIncludes = true) {

  override def name: String = BASTWriter.name

  private val writer = new ByteBufferWriter()
  private val stringTable = StringTable()
  private var lastLocation: At = At.empty
  private var nodeCount: Int = 0

  // Reserve space for header - we'll backpatch it later
  writer.writeRawBytes(new Array[Byte](HEADER_SIZE))

  override def process(definition: RiddlValue, parents: ParentStack): Unit = {
    nodeCount += 1
    definition match {
      // Root containers
      case n: Nebula => writeNebula(n)
      case r: Root => writeRoot(r)
      case i: Include[?] => writeInclude(i)

      // Vital definitions
      case d: Domain => writeDomain(d)
      case c: Context => writeContext(c)
      case e: Entity => writeEntity(e)
      case m: Module => writeModule(m)
      case epic: Epic => writeEpic(epic)

      // Types
      case t: Type => writeType(t)
      case f: Field => writeField(f)
      case enumerator: Enumerator => writeEnumerator(enumerator)
      case method: Method => writeMethod(method)
      case arg: MethodArgument => writeMethodArgument(arg)

      // Processors
      case a: Adaptor => writeAdaptor(a)
      case fn: Function => writeFunction(fn)
      case proj: Projector => writeProjector(proj)
      case repo: Repository => writeRepository(repo)
      case s: Streamlet => writeStreamlet(s)
      case saga: Saga => writeSaga(saga)

      // Handler components
      case h: Handler => writeHandler(h)
      case st: State => writeState(st)
      case inv: Invariant => writeInvariant(inv)

      // OnClauses
      case oc: OnInitializationClause => writeOnInitializationClause(oc)
      case oc: OnTerminationClause => writeOnTerminationClause(oc)
      case oc: OnMessageClause => writeOnMessageClause(oc)
      case oc: OnOtherClause => writeOnOtherClause(oc)

      // Streamlet components
      case inlet: Inlet => writeInlet(inlet)
      case outlet: Outlet => writeOutlet(outlet)
      case conn: Connector => writeConnector(conn)

      // Repository components
      case schema: Schema => writeSchema(schema)

      // Epic/UseCase components
      case uc: UseCase => writeUseCase(uc)
      case us: UserStory => writeUserStory(us)
      case shown: ShownBy => writeShownBy(shown)
      case step: SagaStep => writeSagaStep(step)

      // Interactions
      case i: ParallelInteractions => writeParallelInteractions(i)
      case i: SequentialInteractions => writeSequentialInteractions(i)
      case i: OptionalInteractions => writeOptionalInteractions(i)
      case i: VagueInteraction => writeVagueInteraction(i)
      case i: SendMessageInteraction => writeSendMessageInteraction(i)
      case i: ArbitraryInteraction => writeArbitraryInteraction(i)
      case i: SelfInteraction => writeSelfInteraction(i)
      case i: FocusOnGroupInteraction => writeFocusOnGroupInteraction(i)
      case i: DirectUserToURLInteraction => writeDirectUserToURLInteraction(i)
      case i: ShowOutputInteraction => writeShowOutputInteraction(i)
      case i: SelectInputInteraction => writeSelectInputInteraction(i)
      case i: TakeInputInteraction => writeTakeInputInteraction(i)

      // UI Components
      case g: Group => writeGroup(g)
      case cg: ContainedGroup => writeContainedGroup(cg)
      case input: Input => writeInput(input)
      case output: Output => writeOutput(output)

      // Statements
      case s: ArbitraryStatement => writeArbitraryStatement(s)
      case s: ErrorStatement => writeErrorStatement(s)
      case s: FocusStatement => writeFocusStatement(s)
      case s: SetStatement => writeSetStatement(s)
      case s: ReturnStatement => writeReturnStatement(s)
      case s: SendStatement => writeSendStatement(s)
      case s: ReplyStatement => writeReplyStatement(s)
      case s: MorphStatement => writeMorphStatement(s)
      case s: BecomeStatement => writeBecomeStatement(s)
      case s: TellStatement => writeTellStatement(s)
      case s: CallStatement => writeCallStatement(s)
      case s: ForEachStatement => writeForEachStatement(s)
      case s: IfThenElseStatement => writeIfThenElseStatement(s)
      case s: StopStatement => writeStopStatement(s)
      case s: ReadStatement => writeReadStatement(s)
      case s: WriteStatement => writeWriteStatement(s)
      case s: CodeStatement => writeCodeStatement(s)

      // References
      case r: AuthorRef => writeAuthorRef(r)
      case r: TypeRef => writeTypeRef(r)
      case r: FieldRef => writeFieldRef(r)
      case r: ConstantRef => writeConstantRef(r)
      case r: CommandRef => writeCommandRef(r)
      case r: EventRef => writeEventRef(r)
      case r: QueryRef => writeQueryRef(r)
      case r: ResultRef => writeResultRef(r)
      case r: RecordRef => writeRecordRef(r)
      case r: AdaptorRef => writeAdaptorRef(r)
      case r: FunctionRef => writeFunctionRef(r)
      case r: HandlerRef => writeHandlerRef(r)
      case r: StateRef => writeStateRef(r)
      case r: EntityRef => writeEntityRef(r)
      case r: RepositoryRef => writeRepositoryRef(r)
      case r: ProjectorRef => writeProjectorRef(r)
      case r: ContextRef => writeContextRef(r)
      case r: StreamletRef => writeStreamletRef(r)
      case r: InletRef => writeInletRef(r)
      case r: OutletRef => writeOutletRef(r)
      case r: SagaRef => writeSagaRef(r)
      case r: UserRef => writeUserRef(r)
      case r: EpicRef => writeEpicRef(r)
      case r: GroupRef => writeGroupRef(r)
      case r: InputRef => writeInputRef(r)
      case r: OutputRef => writeOutputRef(r)
      case r: DomainRef => writeDomainRef(r)

      // Metadata & Documentation
      case bd: BriefDescription => writeBriefDescription(bd)
      case bd: BlockDescription => writeBlockDescription(bd)
      case ud: URLDescription => writeURLDescription(ud)
      case c: LineComment => writeLineComment(c)
      case c: InlineComment => writeInlineComment(c)
      case opt: OptionValue => writeOptionValue(opt)
      case term: Term => writeTerm(term)

      // Attachments
      case a: FileAttachment => writeFileAttachment(a)
      case a: StringAttachment => writeStringAttachment(a)
      case a: ULIDAttachment => writeULIDAttachment(a)

      // Simple values
      case id: Identifier => writeIdentifier(id)
      case pid: PathIdentifier => writePathIdentifier(pid)
      case ls: LiteralString => writeLiteralString(ls)

      // Authors and Users
      case a: Author => writeAuthor(a)
      case u: User => writeUser(u)

      // Constants
      case c: Constant => writeConstant(c)

      // Relationships
      case r: Relationship => writeRelationship(r)

      // Type expressions (not handled by writeTypeExpression)
      case te: TypeExpression => writeTypeExpression(te)

      // Streamlet shapes
      case s: Void => writeVoid(s)
      case s: Source => writeSource(s)
      case s: Sink => writeSink(s)
      case s: Flow => writeFlow(s)
      case s: Merge => writeMerge(s)
      case s: Split => writeSplit(s)
      case s: Router => writeRouter(s)

      // Adaptor directions
      case d: InboundAdaptor => writeInboundAdaptor(d)
      case d: OutboundAdaptor => writeOutboundAdaptor(d)

      // Containers (should generally not appear standalone)
      case sc: SimpleContainer[?] => writeSimpleContainer(sc)

      case _ =>
        // Log unhandled types for debugging
        println(s"Unhandled node type in BASTWriter: ${definition.getClass.getSimpleName} at ${definition.loc}")
    }
  }

  // Override traverse to write metadata count AFTER contents items
  // and to handle nodes with multiple Contents fields (SagaStep, IfThenElseStatement)
  override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case root: Root =>
        process(root, parents)
        parents.push(root)
        root.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        // Write metadata count for Root (always 0)
        writeMetadataCount(Contents.empty[MetaData]())

      // SagaStep has TWO Contents fields: doStatements and undoStatements
      // Must write count-items, count-items to match reader expectations
      // SagaStep is not a Branch, so no push/pop needed
      case ss: SagaStep =>
        process(ss, parents)
        // Write doStatements count then items
        writeContents(ss.doStatements)
        ss.doStatements.toSeq.foreach { value => traverse(value, parents) }
        // Write undoStatements count then items
        writeContents(ss.undoStatements)
        ss.undoStatements.toSeq.foreach { value => traverse(value, parents) }
        // Write metadata
        writeMetadataCount(ss.metadata)

      // IfThenElseStatement has TWO Contents fields: thens and elses
      // Must write count-items, count-items to match reader expectations
      // IfThenElseStatement is not a Branch, so no push/pop needed
      case ite: IfThenElseStatement =>
        process(ite, parents)
        // Write thens count then items
        writeContents(ite.thens)
        ite.thens.toSeq.foreach { value => traverse(value, parents) }
        // Write elses count then items
        writeContents(ite.elses)
        ite.elses.toSeq.foreach { value => traverse(value, parents) }
        // IfThenElseStatement is a Statement, no metadata

      // ForEachStatement has a do_ Contents field that must be traversed
      case fe: ForEachStatement =>
        process(fe, parents)
        fe.do_.toSeq.foreach { value => traverse(value, parents) }
        // ForEachStatement is a Statement, no metadata

      // Handler extends Branch[HandlerContents] but NOT WithMetaData, so handle separately
      case h: Handler =>
        process(h, parents)
        parents.push(h)
        h.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(h.metadata)

      // OnClauses extend Branch[Statements] but NOT WithMetaData, so handle separately
      // They have metadata fields that need to be written
      case oc: OnInitializationClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(oc.metadata)

      case oc: OnTerminationClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(oc.metadata)

      case oc: OnMessageClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(oc.metadata)

      case oc: OnOtherClause =>
        process(oc, parents)
        parents.push(oc)
        oc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(oc.metadata)

      // Type extends Branch[TypeContents] but NOT WithMetaData
      // Its contents are computed from typEx, not stored, so no traversal needed
      case t: Type =>
        process(t, parents)
        writeMetadataCount(t.metadata)

      // UseCase extends Branch[UseCaseContents] but NOT WithMetaData
      case uc: UseCase =>
        process(uc, parents)
        parents.push(uc)
        uc.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(uc.metadata)

      // Group extends Branch[OccursInGroup] but NOT WithMetaData
      case g: Group =>
        process(g, parents)
        parents.push(g)
        g.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(g.metadata)

      // Output extends Branch[OccursInOutput] but NOT WithMetaData
      case o: Output =>
        process(o, parents)
        parents.push(o)
        o.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(o.metadata)

      // Input extends Branch[OccursInInput] but NOT WithMetaData
      case i: Input =>
        process(i, parents)
        parents.push(i)
        i.contents.foreach { value => traverse(value, parents) }
        parents.pop()
        writeMetadataCount(i.metadata)

      case branch: Branch[?] with WithMetaData =>
        process(branch, parents)  // Writes node data + contents count
        parents.push(branch)
        branch.contents.foreach { value => traverse(value, parents) }  // Write contents items
        parents.pop()
        // Now write metadata count and items AFTER contents items
        writeMetadataCount(branch.metadata)

      // Non-Branch leaf definitions with metadata (Author, User, Term, State, Invariant, etc.)
      // These need their metadata written but have no contents to traverse
      case wm: WithMetaData =>
        process(wm, parents)
        writeMetadataCount(wm.metadata)

      case _ =>
        super.traverse(definition, parents)  // Use default traversal for non-branches
    }
  }

  // Write metadata count and items
  private def writeMetadataCount(metadata: Contents[MetaData]): Unit = {
    writer.writeVarInt(metadata.length)
    // Write each metadata item inline
    metadata.toSeq.foreach { item =>
      process(item, ParentStack())  // Write metadata items with empty parent stack
    }
  }

  override def postProcess(root: PassRoot): Unit = {
    // Write string table at current position
    val stringTableOffset = writer.position
    stringTable.writeTo(writer)

    // Create and write header
    val bytes = writer.toByteArray
    val checksum = BinaryFormat.calculateChecksum(bytes, HEADER_SIZE, bytes.length - HEADER_SIZE)

    val header = BinaryFormat.Header(
      magic = MAGIC_BYTES,
      versionMajor = VERSION_MAJOR,
      versionMinor = VERSION_MINOR,
      flags = 0,
      stringTableOffset = stringTableOffset,
      rootOffset = HEADER_SIZE,
      fileSize = writer.size,
      checksum = checksum,
      reserved = Array.fill(8)(0.toByte)
    )

    // Write header at the beginning using a separate writer
    val headerBytes = BinaryFormat.serializeHeader(header)
    val finalBytes = writer.toByteArray
    System.arraycopy(headerBytes, 0, finalBytes, 0, HEADER_SIZE)

    // Update the writer's buffer with the final bytes
    writer.clear()
    writer.writeRawBytes(finalBytes)
  }

  override def result(root: PassRoot): BASTOutput = {
    println(s"[info] BAST serialization complete: $nodeCount nodes, ${writer.size} bytes")
    BASTOutput(root, Messages.empty, writer.toByteArray, nodeCount, stringTable.size)
  }

  // ========== Root Container Serialization ==========

  private def writeRoot(r: Root): Unit = {
    writer.writeU8(NODE_NEBULA) // Root uses same tag as Nebula
    writeLocation(r.loc)
    writeIdentifier(Identifier.empty) // Root has no id
    writeContents(r.contents)
    // Metadata for Root is always empty, so no need to store it
  }

  private def writeNebula(n: Nebula): Unit = {
    writer.writeU8(NODE_NEBULA)
    writeLocation(n.loc)
    writeIdentifier(Identifier.empty) // Nebula has no explicit id field
    writeContents(n.contents)
  }

  private def writeInclude[T <: RiddlValue](i: Include[T]): Unit = {
    writer.writeU8(NODE_INCLUDE)
    writeLocation(i.loc)
    writeURL(i.origin)
    writeContents(i.contents)
  }

  // ========== Definition Serialization ==========

  private def writeDomain(d: Domain): Unit = {
    writer.writeU8(NODE_DOMAIN)
    writeLocation(d.loc)
    writeIdentifier(d.id)
    writeContents(d.contents)
    // Metadata will be written by traverse() after contents items
  }

  private def writeContext(c: Context): Unit = {
    writer.writeU8(NODE_CONTEXT)
    writeLocation(c.loc)
    writeIdentifier(c.id)
    writeContents(c.contents)
  }

  private def writeEntity(e: Entity): Unit = {
    writer.writeU8(NODE_ENTITY)
    writeLocation(e.loc)
    writeIdentifier(e.id)
    writeContents(e.contents)
  }

  private def writeModule(m: Module): Unit = {
    writer.writeU8(NODE_MODULE)
    writeLocation(m.loc)
    writeIdentifier(m.id)
    writeContents(m.contents)
  }

  private def writeType(t: Type): Unit = {
    writer.writeU8(NODE_TYPE)
    writeLocation(t.loc)
    writeIdentifier(t.id)
    writeTypeExpression(t.typEx)
  }

  private def writeFunction(f: Function): Unit = {
    writer.writeU8(NODE_FUNCTION)
    writeLocation(f.loc)
    writeIdentifier(f.id)
    writeOption(f.input)((agg: Aggregation) => writeTypeExpression(agg))
    writeOption(f.output)((agg: Aggregation) => writeTypeExpression(agg))
    writeContents(f.contents)
  }

  private def writeAdaptor(a: Adaptor): Unit = {
    writer.writeU8(NODE_ADAPTOR)
    writeLocation(a.loc)
    writeIdentifier(a.id)
    a.direction match {
      case _: InboundAdaptor => writer.writeU8(ADAPTOR_INBOUND)
      case _: OutboundAdaptor => writer.writeU8(ADAPTOR_OUTBOUND)
    }
    writeContextRef(a.referent)
    writeContents(a.contents)
  }

  private def writeSaga(s: Saga): Unit = {
    writer.writeU8(NODE_SAGA)
    writeLocation(s.loc)
    writeIdentifier(s.id)
    writeOption(s.input)((agg: Aggregation) => writeTypeExpression(agg))
    writeOption(s.output)((agg: Aggregation) => writeTypeExpression(agg))
    writeContents(s.contents)
  }

  private def writeProcessor(p: Processor[?]): Unit = {
    writer.writeU8(NODE_PROCESSOR)
    writeLocation(p.loc)
    writeIdentifier(p.id)
    writeContents(p.contents)
  }

  private def writeProjector(p: Projector): Unit = {
    writer.writeU8(NODE_PROJECTOR)
    writeLocation(p.loc)
    writeIdentifier(p.id)
    writeContents(p.contents)
  }

  private def writeRepository(r: Repository): Unit = {
    writer.writeU8(NODE_REPOSITORY)
    writeLocation(r.loc)
    writeIdentifier(r.id)
    writeContents(r.contents)
  }

  private def writeStreamlet(s: Streamlet): Unit = {
    writer.writeU8(NODE_STREAMLET)
    writeLocation(s.loc)
    writeIdentifier(s.id)
    // Write shape tag
    s.shape match {
      case _: Void => writer.writeU8(STREAMLET_VOID)
      case _: Source => writer.writeU8(STREAMLET_SOURCE)
      case _: Sink => writer.writeU8(STREAMLET_SINK)
      case _: Flow => writer.writeU8(STREAMLET_FLOW)
      case _: Merge => writer.writeU8(STREAMLET_MERGE)
      case _: Split => writer.writeU8(STREAMLET_SPLIT)
      case _: Router => writer.writeU8(STREAMLET_VOID) // Router not in tags
    }
    writeContents(s.contents)
  }

  private def writeEpic(e: Epic): Unit = {
    writer.writeU8(NODE_EPIC)
    writeLocation(e.loc)
    writeIdentifier(e.id)
    writeUserStory(e.userStory)
    writeContents(e.contents)
  }

  // ========== Leaf Definition Serialization ==========

  private def writeAuthor(a: Author): Unit = {
    writer.writeU8(NODE_AUTHOR)
    writeLocation(a.loc)
    writeIdentifier(a.id)
    writeLiteralString(a.name)
    writeLiteralString(a.email)
    writeOption(a.organization)(writeLiteralString)
    writeOption(a.title)(writeLiteralString)
    writeOption(a.url)(writeURL)
  }

  private def writeUser(u: User): Unit = {
    writer.writeU8(NODE_USER)
    writeLocation(u.loc)
    writeIdentifier(u.id)
    writeLiteralString(u.is_a)
  }

  private def writeTerm(t: Term): Unit = {
    writer.writeU8(NODE_TERM)
    writeLocation(t.loc)
    writeIdentifier(t.id)
    writeSeq(t.definition)(writeLiteralString)
  }

  private def writeRelationship(r: Relationship): Unit = {
    writer.writeU8(NODE_PIPE) // Reusing PIPE tag for relationship
    writeLocation(r.loc)
    writeIdentifier(r.id)
    writeProcessorRef(r.withProcessor)
    writer.writeU8(r.cardinality.ordinal.toByte)
    writeOption(r.label)(writeLiteralString)
  }

  private def writeConstant(c: Constant): Unit = {
    writer.writeU8(NODE_FIELD) // Constants similar to fields
    writeLocation(c.loc)
    writeIdentifier(c.id)
    writeTypeExpression(c.typeEx)
    writeLiteralString(c.value)
  }

  // ========== Type Component Serialization ==========

  private def writeField(f: Field): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(f.loc)
    writeIdentifier(f.id)
    writeTypeExpression(f.typeEx)
  }

  private def writeEnumerator(e: Enumerator): Unit = {
    writer.writeU8(NODE_ENUMERATOR)
    writeLocation(e.loc)
    writeIdentifier(e.id)
    writeOption(e.enumVal)((v: Long) => writer.writeVarLong(v))
  }

  private def writeMethod(m: Method): Unit = {
    writer.writeU8(NODE_FIELD) // Methods similar to fields
    writeLocation(m.loc)
    writeIdentifier(m.id)
    writeTypeExpression(m.typeEx)
    writeSeq(m.args)(writeMethodArgument)
  }

  private def writeMethodArgument(a: MethodArgument): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(a.loc)
    writeString(a.name)
    writeTypeExpression(a.typeEx)
  }

  // ========== Handler Component Serialization ==========

  private def writeHandler(h: Handler): Unit = {
    writer.writeU8(NODE_HANDLER)
    writeLocation(h.loc)
    writeIdentifier(h.id)
    writeContents(h.contents)
  }

  private def writeState(s: State): Unit = {
    writer.writeU8(NODE_STATE)
    writeLocation(s.loc)
    writeIdentifier(s.id)
    writeTypeRef(s.typ)
  }

  private def writeInvariant(i: Invariant): Unit = {
    writer.writeU8(NODE_INVARIANT)
    writeLocation(i.loc)
    writeIdentifier(i.id)
    writeOption(i.condition)(writeLiteralString)
  }

  // ========== OnClause Serialization ==========

  private def writeOnInitializationClause(oc: OnInitializationClause): Unit = {
    writer.writeU8(NODE_ON_CLAUSE)
    writer.writeU8(0) // Init clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  private def writeOnTerminationClause(oc: OnTerminationClause): Unit = {
    writer.writeU8(NODE_ON_CLAUSE)
    writer.writeU8(1) // Term clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  private def writeOnMessageClause(oc: OnMessageClause): Unit = {
    writer.writeU8(NODE_ON_CLAUSE)
    writer.writeU8(2) // Message clause type
    writeLocation(oc.loc)
    writeMessageRef(oc.msg)
    // Write from field: Option[(Option[Identifier], Reference[Definition])]
    writeOption(oc.from) { case (optId, ref) =>
      writeOption(optId)(writeIdentifier)
      writeReference(ref)
    }
    writeContents(oc.contents)
  }

  private def writeOnOtherClause(oc: OnOtherClause): Unit = {
    writer.writeU8(NODE_ON_CLAUSE)
    writer.writeU8(3) // Other clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  // ========== Streamlet Component Serialization ==========

  private def writeInlet(i: Inlet): Unit = {
    writer.writeU8(NODE_INLET)
    writeLocation(i.loc)
    writeIdentifier(i.id)
    writeTypeRef(i.type_)
  }

  private def writeOutlet(o: Outlet): Unit = {
    writer.writeU8(NODE_OUTLET)
    writeLocation(o.loc)
    writeIdentifier(o.id)
    writeTypeRef(o.type_)
  }

  private def writeConnector(c: Connector): Unit = {
    writer.writeU8(NODE_CONNECTOR)
    writeLocation(c.loc)
    writeIdentifier(c.id)
    writeOutletRef(c.from)
    writeInletRef(c.to)
  }

  // ========== Repository Component Serialization ==========

  private def writeSchema(s: Schema): Unit = {
    writer.writeU8(NODE_SCHEMA)
    writer.writeU8(s.schemaKind.ordinal) // Subtype: 0=Relational, 1=Document, 2=Graphical
    writeLocation(s.loc)
    writeIdentifier(s.id)
    // Write data map
    writer.writeVarInt(s.data.size)
    s.data.foreach { case (id, tref) =>
      writeIdentifier(id)
      writeTypeRef(tref)
    }
    // Write links map
    writer.writeVarInt(s.links.size)
    s.links.foreach { case (id, (fr1, fr2)) =>
      writeIdentifier(id)
      writeFieldRef(fr1)
      writeFieldRef(fr2)
    }
    // Write indices
    writeSeq(s.indices)(writeFieldRef)
  }

  // ========== Epic/UseCase Component Serialization ==========

  private def writeUseCase(uc: UseCase): Unit = {
    writer.writeU8(NODE_EPIC) // UseCase similar to Epic
    writeLocation(uc.loc)
    writeIdentifier(uc.id)
    writeUserStory(uc.userStory)
    writeContents(uc.contents)
  }

  private def writeUserStory(us: UserStory): Unit = {
    writer.writeU8(NODE_USER) // User story relates to user
    writeLocation(us.loc)
    writeUserRef(us.user)
    writeLiteralString(us.capability)
    writeLiteralString(us.benefit)
  }

  private def writeShownBy(sb: ShownBy): Unit = {
    writer.writeU8(NODE_OUTLET) // Reusing outlet tag
    writeLocation(sb.loc)
    writeSeq(sb.urls)(writeURL)
  }

  private def writeSagaStep(ss: SagaStep): Unit = {
    writer.writeU8(NODE_SAGA_STEP)
    writeLocation(ss.loc)
    writeIdentifier(ss.id)
    // NOTE: doStatements and undoStatements are written by traverse() override
    // to properly interleave count-items, count-items
  }

  // ========== Interaction Serialization ==========

  private def writeParallelInteractions(pi: ParallelInteractions): Unit = {
    writer.writeU8(NODE_PIPE) // Interactions like connectors
    writer.writeU8(0) // Parallel type
    writeLocation(pi.loc)
    writeContents(pi.contents)
  }

  private def writeSequentialInteractions(si: SequentialInteractions): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(1) // Sequential type
    writeLocation(si.loc)
    writeContents(si.contents)
  }

  private def writeOptionalInteractions(oi: OptionalInteractions): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(2) // Optional type
    writeLocation(oi.loc)
    writeContents(oi.contents)
  }

  private def writeVagueInteraction(vi: VagueInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(10) // Vague interaction type
    writeLocation(vi.loc)
    writeLiteralString(vi.from)
    writeLiteralString(vi.relationship)
    writeLiteralString(vi.to)
  }

  private def writeSendMessageInteraction(smi: SendMessageInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(11) // Send message interaction
    writeLocation(smi.loc)
    writeReference(smi.from)
    writeMessageRef(smi.message)
    writeProcessorRef(smi.to)
  }

  private def writeArbitraryInteraction(ai: ArbitraryInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(12) // Arbitrary interaction
    writeLocation(ai.loc)
    writeReference(ai.from)
    writeLiteralString(ai.relationship)
    writeReference(ai.to)
  }

  private def writeSelfInteraction(si: SelfInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(13) // Self interaction
    writeLocation(si.loc)
    writeReference(si.from)
    writeLiteralString(si.relationship)
  }

  private def writeFocusOnGroupInteraction(fgi: FocusOnGroupInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(14) // Focus on group
    writeLocation(fgi.loc)
    writeUserRef(fgi.from)
    writeGroupRef(fgi.to)
  }

  private def writeDirectUserToURLInteraction(dui: DirectUserToURLInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(15) // Direct to URL
    writeLocation(dui.loc)
    writeUserRef(dui.from)
    writeURL(dui.url)
  }

  private def writeShowOutputInteraction(soi: ShowOutputInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(16) // Show output
    writeLocation(soi.loc)
    writeOutputRef(soi.from)
    writeLiteralString(soi.relationship)
    writeUserRef(soi.to)
  }

  private def writeSelectInputInteraction(sii: SelectInputInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(17) // Select input
    writeLocation(sii.loc)
    writeUserRef(sii.from)
    writeInputRef(sii.to)
  }

  private def writeTakeInputInteraction(tii: TakeInputInteraction): Unit = {
    writer.writeU8(NODE_PIPE)
    writer.writeU8(18) // Take input
    writeLocation(tii.loc)
    writeUserRef(tii.from)
    writeInputRef(tii.to)
  }

  // ========== UI Component Serialization ==========

  private def writeGroup(g: Group): Unit = {
    writer.writeU8(NODE_GROUP)
    writeLocation(g.loc)
    writeString(g.alias)
    writeIdentifier(g.id)
    writeContents(g.contents)
  }

  private def writeContainedGroup(cg: ContainedGroup): Unit = {
    writer.writeU8(NODE_GROUP)
    writeLocation(cg.loc)
    writeIdentifier(cg.id)
    writeGroupRef(cg.group)
  }

  private def writeInput(i: Input): Unit = {
    writer.writeU8(NODE_INPUT)
    writeLocation(i.loc)
    writeString(i.nounAlias)
    writeIdentifier(i.id)
    writeString(i.verbAlias)
    writeTypeRef(i.takeIn)
    writeContents(i.contents)
  }

  private def writeOutput(o: Output): Unit = {
    writer.writeU8(NODE_OUTPUT)
    writeLocation(o.loc)
    writeString(o.nounAlias)
    writeIdentifier(o.id)
    writeString(o.verbAlias)
    // Handle union type: TypeRef | ConstantRef | LiteralString
    o.putOut match {
      case tr: TypeRef =>
        writer.writeU8(0)
        writeTypeRef(tr)
      case cr: ConstantRef =>
        writer.writeU8(1)
        writeConstantRef(cr)
      case ls: LiteralString =>
        writer.writeU8(2)
        writeLiteralString(ls)
    }
    writeContents(o.contents)
  }

  // ========== Statement Serialization ==========

  // Marker value to distinguish statements from handlers
  // Statements: NODE_HANDLER, 0xFF, subtype, loc, ...
  // Handlers: NODE_HANDLER, loc (no 0xFF marker), ...
  // Using 255 (0xFF) as it's distinct from valid location/string data
  private val STATEMENT_MARKER: Int = 255

  private def writeArbitraryStatement(s: ArbitraryStatement): Unit = {
    writer.writeU8(NODE_HANDLER) // Statements within handlers
    writer.writeU8(STATEMENT_MARKER) // Marker to identify as statement
    writer.writeU8(0) // Arbitrary statement type
    writeLocation(s.loc)
    writeLiteralString(s.what)
  }

  private def writeErrorStatement(s: ErrorStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(1) // Error statement
    writeLocation(s.loc)
    writeLiteralString(s.message)
  }

  private def writeFocusStatement(s: FocusStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(2) // Focus statement
    writeLocation(s.loc)
    writeGroupRef(s.group)
  }

  private def writeSetStatement(s: SetStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(3) // Set statement
    writeLocation(s.loc)
    writeFieldRef(s.field)
    writeLiteralString(s.value)
  }

  private def writeReturnStatement(s: ReturnStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(4) // Return statement
    writeLocation(s.loc)
    writeLiteralString(s.value)
  }

  private def writeSendStatement(s: SendStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(5) // Send statement
    writeLocation(s.loc)
    writeMessageRef(s.msg)
    writePortletRef(s.portlet)
  }

  private def writeReplyStatement(s: ReplyStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(6) // Reply statement
    writeLocation(s.loc)
    writeMessageRef(s.message)
  }

  private def writeMorphStatement(s: MorphStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(7) // Morph statement
    writeLocation(s.loc)
    writeEntityRef(s.entity)
    writeStateRef(s.state)
    writeMessageRef(s.value)
  }

  private def writeBecomeStatement(s: BecomeStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(8) // Become statement
    writeLocation(s.loc)
    writeEntityRef(s.entity)
    writeHandlerRef(s.handler)
  }

  private def writeTellStatement(s: TellStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(9) // Tell statement
    writeLocation(s.loc)
    writeMessageRef(s.msg)
    writeProcessorRef(s.processorRef)
  }

  private def writeCallStatement(s: CallStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(10) // Call statement
    writeLocation(s.loc)
    writeFunctionRef(s.func)
  }

  private def writeForEachStatement(s: ForEachStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(11) // ForEach statement
    writeLocation(s.loc)
    // Handle union type: FieldRef | OutletRef | InletRef
    s.ref match {
      case fr: FieldRef =>
        writer.writeU8(0)
        writeFieldRef(fr)
      case or: OutletRef =>
        writer.writeU8(1)
        writeOutletRef(or)
      case ir: InletRef =>
        writer.writeU8(2)
        writeInletRef(ir)
    }
    writeContents(s.do_)
  }

  private def writeIfThenElseStatement(s: IfThenElseStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(12) // IfThenElse statement
    writeLocation(s.loc)
    writeLiteralString(s.cond)
    // NOTE: thens and elses counts/items are written by traverse() override
    // to properly interleave count-items, count-items
  }

  private def writeStopStatement(s: StopStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(13) // Stop statement
    writeLocation(s.loc)
  }

  private def writeReadStatement(s: ReadStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(14) // Read statement
    writeLocation(s.loc)
    writeString(s.keyword)
    writeLiteralString(s.what)
    writeTypeRef(s.from)
    writeLiteralString(s.where)
  }

  private def writeWriteStatement(s: WriteStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(15) // Write statement
    writeLocation(s.loc)
    writeString(s.keyword)
    writeLiteralString(s.what)
    writeTypeRef(s.to)
  }

  private def writeCodeStatement(s: CodeStatement): Unit = {
    writer.writeU8(NODE_HANDLER)
    writer.writeU8(STATEMENT_MARKER)
    writer.writeU8(16) // Code statement
    writeLocation(s.loc)
    writeLiteralString(s.language)
    writeString(s.body)
  }

  // ========== Reference Serialization ==========

  private def writeAuthorRef(r: AuthorRef): Unit = {
    writer.writeU8(NODE_AUTHOR)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeTypeRef(r: TypeRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifier(r.pathId)
  }

  private def writeFieldRef(r: FieldRef): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeConstantRef(r: ConstantRef): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeCommandRef(r: CommandRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writer.writeU8(0) // Command type
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeEventRef(r: EventRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writer.writeU8(1) // Event type
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeQueryRef(r: QueryRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writer.writeU8(2) // Query type
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeResultRef(r: ResultRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writer.writeU8(3) // Result type
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeRecordRef(r: RecordRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writer.writeU8(4) // Record type
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeAdaptorRef(r: AdaptorRef): Unit = {
    writer.writeU8(NODE_ADAPTOR)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeFunctionRef(r: FunctionRef): Unit = {
    writer.writeU8(NODE_FUNCTION)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeHandlerRef(r: HandlerRef): Unit = {
    writer.writeU8(NODE_HANDLER)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeStateRef(r: StateRef): Unit = {
    writer.writeU8(NODE_STATE)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeEntityRef(r: EntityRef): Unit = {
    writer.writeU8(NODE_ENTITY)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeRepositoryRef(r: RepositoryRef): Unit = {
    writer.writeU8(NODE_REPOSITORY)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeProjectorRef(r: ProjectorRef): Unit = {
    writer.writeU8(NODE_PROJECTOR)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeContextRef(r: ContextRef): Unit = {
    writer.writeU8(NODE_CONTEXT)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeStreamletRef(r: StreamletRef): Unit = {
    writer.writeU8(NODE_STREAMLET)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifier(r.pathId)
  }

  private def writeInletRef(r: InletRef): Unit = {
    writer.writeU8(NODE_INLET)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeOutletRef(r: OutletRef): Unit = {
    writer.writeU8(NODE_OUTLET)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeSagaRef(r: SagaRef): Unit = {
    writer.writeU8(NODE_SAGA)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeUserRef(r: UserRef): Unit = {
    writer.writeU8(NODE_USER)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeEpicRef(r: EpicRef): Unit = {
    writer.writeU8(NODE_EPIC)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  private def writeGroupRef(r: GroupRef): Unit = {
    writer.writeU8(NODE_GROUP)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifier(r.pathId)
  }

  private def writeInputRef(r: InputRef): Unit = {
    writer.writeU8(NODE_INPUT)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifier(r.pathId)
  }

  private def writeOutputRef(r: OutputRef): Unit = {
    writer.writeU8(NODE_OUTPUT)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifier(r.pathId)
  }

  private def writeDomainRef(r: DomainRef): Unit = {
    writer.writeU8(NODE_DOMAIN)
    writeLocation(r.loc)
    writePathIdentifier(r.pathId)
  }

  // Generic reference writer for polymorphic cases
  private def writeReference(r: Reference[?]): Unit = {
    r match {
      case ar: AuthorRef => writeAuthorRef(ar)
      case tr: TypeRef => writeTypeRef(tr)
      case fr: FieldRef => writeFieldRef(fr)
      case cr: ConstantRef => writeConstantRef(cr)
      case cmdr: CommandRef => writeCommandRef(cmdr)
      case er: EventRef => writeEventRef(er)
      case qr: QueryRef => writeQueryRef(qr)
      case rr: ResultRef => writeResultRef(rr)
      case recr: RecordRef => writeRecordRef(recr)
      case adr: AdaptorRef => writeAdaptorRef(adr)
      case fnr: FunctionRef => writeFunctionRef(fnr)
      case hr: HandlerRef => writeHandlerRef(hr)
      case sr: StateRef => writeStateRef(sr)
      case entr: EntityRef => writeEntityRef(entr)
      case repr: RepositoryRef => writeRepositoryRef(repr)
      case pr: ProjectorRef => writeProjectorRef(pr)
      case cr: ContextRef => writeContextRef(cr)
      case slr: StreamletRef => writeStreamletRef(slr)
      case ir: InletRef => writeInletRef(ir)
      case or: OutletRef => writeOutletRef(or)
      case sgr: SagaRef => writeSagaRef(sgr)
      case ur: UserRef => writeUserRef(ur)
      case epr: EpicRef => writeEpicRef(epr)
      case gr: GroupRef => writeGroupRef(gr)
      case inr: InputRef => writeInputRef(inr)
      case outr: OutputRef => writeOutputRef(outr)
      case dr: DomainRef => writeDomainRef(dr)
      case _ =>
        println(s"Unhandled reference type: ${r.getClass.getSimpleName} at ${r.loc}")
    }
  }

  private def writeMessageRef(r: MessageRef): Unit = {
    r match {
      case cr: CommandRef => writeCommandRef(cr)
      case er: EventRef => writeEventRef(er)
      case qr: QueryRef => writeQueryRef(qr)
      case rr: ResultRef => writeResultRef(rr)
      case recr: RecordRef => writeRecordRef(recr)
      case _ =>
        println(s"Unhandled message ref type: ${r.getClass.getSimpleName} at ${r.loc}")
    }
  }

  private def writeProcessorRef(r: ProcessorRef[?]): Unit = {
    r match {
      case ar: AdaptorRef => writeAdaptorRef(ar)
      case er: EntityRef => writeEntityRef(er)
      case rr: RepositoryRef => writeRepositoryRef(rr)
      case pr: ProjectorRef => writeProjectorRef(pr)
      case cr: ContextRef => writeContextRef(cr)
      case sr: StreamletRef => writeStreamletRef(sr)
    }
  }

  private def writePortletRef(r: PortletRef[?]): Unit = {
    r match {
      case ir: InletRef => writeInletRef(ir)
      case or: OutletRef => writeOutletRef(or)
    }
  }

  // ========== Metadata Serialization ==========

  private def writeBriefDescription(bd: BriefDescription): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(0) // Brief type
    writeLocation(bd.loc)
    writeLiteralString(bd.brief)
  }

  private def writeBlockDescription(bd: BlockDescription): Unit = {
    writer.writeU8(NODE_BLOCK_DESCRIPTION)
    writeLocation(bd.loc)
    writeSeq(bd.lines)(writeLiteralString)
  }

  private def writeURLDescription(ud: URLDescription): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(2) // URL type
    writeLocation(ud.loc)
    writeURL(ud.url)
  }

  private def writeLineComment(c: LineComment): Unit = {
    writer.writeU8(NODE_COMMENT)
    writeLocation(c.loc)
    writeString(c.text)
  }

  private def writeInlineComment(c: InlineComment): Unit = {
    writer.writeU8(NODE_BLOCK_COMMENT)
    writeLocation(c.loc)
    writeSeq(c.lines)(writeString)
  }

  private def writeOptionValue(ov: OptionValue): Unit = {
    writer.writeU8(NODE_DESCRIPTION) // Options are metadata
    writer.writeU8(10) // Option type
    writeLocation(ov.loc)
    writeString(ov.name)
    writeSeq(ov.args)(writeLiteralString)
  }

  // ========== Attachment Serialization ==========

  private def writeFileAttachment(a: FileAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(20) // File attachment
    writeLocation(a.loc)
    writeIdentifier(a.id)
    writeString(a.mimeType)
    writeLiteralString(a.inFile)
  }

  private def writeStringAttachment(a: StringAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(21) // String attachment
    writeLocation(a.loc)
    writeIdentifier(a.id)
    writeString(a.mimeType)
    writeLiteralString(a.value)
  }

  private def writeULIDAttachment(a: ULIDAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(22) // ULID attachment
    writeLocation(a.loc)
    // Write ULID as bytes
    val ulidBytes = a.ulid.toBytes
    writer.writeRawBytes(ulidBytes)
  }

  // ========== Simple Value Serialization ==========

  private def writeLiteralString(ls: LiteralString): Unit = {
    writer.writeU8(NODE_LITERAL_STRING)
    writeLocation(ls.loc)
    writeString(ls.s)
  }

  // ========== Streamlet Shape Serialization ==========

  private def writeVoid(v: Void): Unit = {
    writer.writeU8(STREAMLET_VOID)
    writeLocation(v.loc)
  }

  private def writeSource(s: Source): Unit = {
    writer.writeU8(STREAMLET_SOURCE)
    writeLocation(s.loc)
  }

  private def writeSink(s: Sink): Unit = {
    writer.writeU8(STREAMLET_SINK)
    writeLocation(s.loc)
  }

  private def writeFlow(f: Flow): Unit = {
    writer.writeU8(STREAMLET_FLOW)
    writeLocation(f.loc)
  }

  private def writeMerge(m: Merge): Unit = {
    writer.writeU8(STREAMLET_MERGE)
    writeLocation(m.loc)
  }

  private def writeSplit(s: Split): Unit = {
    writer.writeU8(STREAMLET_SPLIT)
    writeLocation(s.loc)
  }

  private def writeRouter(r: Router): Unit = {
    writer.writeU8(STREAMLET_VOID) // Router not defined, use void
    writeLocation(r.loc)
  }

  // ========== Adaptor Direction Serialization ==========

  private def writeInboundAdaptor(ia: InboundAdaptor): Unit = {
    writer.writeU8(ADAPTOR_INBOUND)
    writeLocation(ia.loc)
  }

  private def writeOutboundAdaptor(oa: OutboundAdaptor): Unit = {
    writer.writeU8(ADAPTOR_OUTBOUND)
    writeLocation(oa.loc)
  }

  // ========== Container Serialization ==========

  private def writeSimpleContainer(sc: SimpleContainer[?]): Unit = {
    writer.writeU8(NODE_NEBULA) // Containers like nebula
    writeLocation(sc.loc)
    writeContents(sc.contents)
  }

  // ========== Type Expression Serialization ==========

  private def writeTypeExpression(te: TypeExpression): Unit = {
    te match {
      // Aggregate types
      case a: Aggregation =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(255) // Subtype marker for plain Aggregation (vs AggregateUseCaseTypeExpression)
        writeLocation(a.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(a.contents.length)
        a.contents.toSeq.foreach { item =>
          process(item, ParentStack())
          // Fields have metadata that needs to be written
          item match {
            case wm: WithMetaData => writeMetadataCount(wm.metadata)
            case _ => ()
          }
        }

      case a: AggregateUseCaseTypeExpression =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(a.usecase.ordinal.toByte)
        writeLocation(a.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(a.contents.length)
        a.contents.toSeq.foreach { item =>
          process(item, ParentStack())
          item match {
            case wm: WithMetaData => writeMetadataCount(wm.metadata)
            case _ => ()
          }
        }

      case e: EntityReferenceTypeExpression =>
        writer.writeU8(TYPE_REF)
        writer.writeU8(10) // Entity ref type
        writeLocation(e.loc)
        writePathIdentifier(e.entity)

      // Collection types
      case alt: Alternation =>
        writer.writeU8(TYPE_ALTERNATION)
        writeLocation(alt.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(alt.of.length)
        alt.of.toSeq.foreach { item =>
          process(item, ParentStack())
          item match {
            case wm: WithMetaData => writeMetadataCount(wm.metadata)
            case _ => ()
          }
        }

      case enumeration: Enumeration =>
        writer.writeU8(TYPE_ENUMERATION)
        writeLocation(enumeration.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(enumeration.enumerators.length)
        enumeration.enumerators.toSeq.foreach { item =>
          process(item, ParentStack())
          // Enumerators always have metadata
          writeMetadataCount(item.metadata)
        }

      case seq: Sequence =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(20) // Sequence subtype
        writeLocation(seq.loc)
        writeTypeExpression(seq.of)

      case m: Mapping =>
        writer.writeU8(TYPE_MAPPING)
        writeLocation(m.loc)
        writeTypeExpression(m.from)
        writeTypeExpression(m.to)

      case s: Set =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(21) // Set subtype
        writeLocation(s.loc)
        writeTypeExpression(s.of)

      case g: Graph =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(22) // Graph subtype
        writeLocation(g.loc)
        writeTypeExpression(g.of)

      case t: Table =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(23) // Table subtype
        writeLocation(t.loc)
        writeTypeExpression(t.of)
        writeSeq(t.dimensions)((d: Long) => writer.writeVarLong(d))

      case r: Replica =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(24) // Replica subtype
        writeLocation(r.loc)
        writeTypeExpression(r.of)

      // Cardinality types
      case opt: Optional =>
        writer.writeU8(TYPE_OPTIONAL)
        writeLocation(opt.loc)
        writeTypeExpression(opt.typeExp)

      case zom: ZeroOrMore =>
        writer.writeU8(TYPE_ZERO_OR_MORE)
        writeLocation(zom.loc)
        writeTypeExpression(zom.typeExp)

      case oom: OneOrMore =>
        writer.writeU8(TYPE_ONE_OR_MORE)
        writeLocation(oom.loc)
        writeTypeExpression(oom.typeExp)

      case sr: SpecificRange =>
        writer.writeU8(TYPE_RANGE)
        writeLocation(sr.loc)
        writeTypeExpression(sr.typeExp)
        writer.writeVarLong(sr.min)
        writer.writeVarLong(sr.max)

      // Aliased type
      case ate: AliasedTypeExpression =>
        writer.writeU8(TYPE_REF)
        writer.writeU8(0) // AliasedTypeExpression subtype (0 = aliased, 10 = entity ref, 99 = abstract, 100 = nothing)
        writeLocation(ate.loc)
        writeString(ate.keyword)
        writePathIdentifier(ate.pathId)

      // Predefined types - String
      case s: String_ =>
        writer.writeU8(TYPE_STRING)
        writer.writeU8(0) // String_ subtype (0 = plain String, 1 = URI, 2 = Blob)
        writeLocation(s.loc)
        writeOption(s.min)((v: Long) => writer.writeVarLong(v))
        writeOption(s.max)((v: Long) => writer.writeVarLong(v))

      case p: Pattern =>
        writer.writeU8(TYPE_PATTERN)
        writeLocation(p.loc)
        writeSeq(p.pattern)(writeLiteralString)

      case u: UniqueId =>
        writer.writeU8(TYPE_UNIQUE_ID)
        writer.writeU8(0) // UniqueId subtype (0 = UniqueId, 1 = UUID, 2 = UserId)
        writeLocation(u.loc)
        writePathIdentifier(u.entityPath)

      case c: Currency =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(50) // Currency subtype
        writeLocation(c.loc)
        writeString(c.country)

      // Predefined types - Boolean and Numbers
      case b: Bool =>
        writer.writeU8(TYPE_BOOL)
        writeLocation(b.loc)

      case n: Number =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(0) // Generic number
        writeLocation(n.loc)

      case i: Integer =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(1) // Integer
        writeLocation(i.loc)

      case w: Whole =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(2) // Whole
        writeLocation(w.loc)

      case n: Natural =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(3) // Natural
        writeLocation(n.loc)

      case rt: RangeType =>
        writer.writeU8(TYPE_RANGE)
        writeLocation(rt.loc)
        writer.writeVarLong(rt.min)
        writer.writeVarLong(rt.max)

      case d: Decimal =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(10) // Decimal
        writeLocation(d.loc)
        writer.writeVarLong(d.whole)
        writer.writeVarLong(d.fractional)

      case r: Real =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(11) // Real
        writeLocation(r.loc)

      // SI units
      case c: Current =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(20) // Current
        writeLocation(c.loc)

      case l: Length =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(21) // Length
        writeLocation(l.loc)

      case l: Luminosity =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(22) // Luminosity
        writeLocation(l.loc)

      case m: Mass =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(23) // Mass
        writeLocation(m.loc)

      case m: Mole =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(24) // Mole
        writeLocation(m.loc)

      case t: Temperature =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(25) // Temperature
        writeLocation(t.loc)

      // Time types
      case d: Date =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(30) // Date
        writeLocation(d.loc)

      case t: Time =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(31) // Time
        writeLocation(t.loc)

      case dt: DateTime =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(32) // DateTime
        writeLocation(dt.loc)

      case zd: ZonedDate =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(33) // ZonedDate
        writeLocation(zd.loc)
        writeOption(zd.zone)(writeLiteralString)

      case zdt: ZonedDateTime =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(34) // ZonedDateTime
        writeLocation(zdt.loc)
        writeOption(zdt.zone)(writeLiteralString)

      case ts: TimeStamp =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(35) // TimeStamp
        writeLocation(ts.loc)

      case d: Duration =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(36) // Duration
        writeLocation(d.loc)

      // Other predefined types
      case u: UUID =>
        writer.writeU8(TYPE_UNIQUE_ID)
        writer.writeU8(1) // UUID
        writeLocation(u.loc)

      case u: URI =>
        writer.writeU8(TYPE_STRING)
        writer.writeU8(1) // URI
        writeLocation(u.loc)
        writeOption(u.scheme)(writeLiteralString)

      case l: Location =>
        writer.writeU8(TYPE_NUMBER)
        writer.writeU8(40) // Location
        writeLocation(l.loc)

      case b: Blob =>
        writer.writeU8(TYPE_STRING)
        writer.writeU8(2) // Blob
        writeLocation(b.loc)
        writer.writeU8(b.blobKind.ordinal.toByte)

      case a: Abstract =>
        writer.writeU8(TYPE_REF)
        writer.writeU8(99) // Abstract
        writeLocation(a.loc)

      case u: UserId =>
        writer.writeU8(TYPE_UNIQUE_ID)
        writer.writeU8(2) // UserId
        writeLocation(u.loc)

      case n: Nothing =>
        writer.writeU8(TYPE_REF)
        writer.writeU8(100) // Nothing
        writeLocation(n.loc)

      case _ =>
        println(s"Unhandled type expression: ${te.getClass.getSimpleName} at ${te.loc}")
    }
  }

  // ========== Helper Serialization Methods ==========

  private def writeLocation(loc: At): Unit = {
    // Delta encoding: store differences from previous location
    if lastLocation.isEmpty then
      // First location: write full data
      // Use origin (path) instead of toExternalForm to preserve relative paths
      writeString(loc.source.origin)
      writer.writeVarInt(loc.offset)
      writer.writeVarInt(loc.line)
      writer.writeVarInt(loc.col)
    else
      // Subsequent locations: write deltas
      // Treat 'empty' origin as same source (identifiers often have empty origin)
      val sameSource = loc.source.origin == lastLocation.source.origin || loc.source.origin == "empty"
      if !sameSource then
        writer.writeU8(1) // Flag: new source file
        writeString(loc.source.origin) // Use origin to preserve relative paths
      else
        writer.writeU8(0) // Flag: same source file
      end if

      // Write deltas (handle negative values by adding offset)
      val offsetDelta = loc.offset - lastLocation.offset
      val lineDelta = loc.line - lastLocation.line
      val colDelta = loc.col - lastLocation.col

      writer.writeVarInt(offsetDelta + 1000000) // Add offset to ensure positive
      writer.writeVarInt(lineDelta + 1000)
      writer.writeVarInt(colDelta + 1000)
    end if

    // Only update lastLocation if this location has a real origin (not 'empty')
    // This prevents corrupting delta encoding when identifiers have placeholder locations
    if loc.source.origin != "empty" then
      lastLocation = loc
    end if
  }

  private def writeIdentifier(id: Identifier): Unit = {
    writer.writeU8(NODE_IDENTIFIER)
    writeLocation(id.loc)
    writeString(id.value)
  }

  private def writePathIdentifier(pid: PathIdentifier): Unit = {
    writer.writeU8(NODE_PATH_IDENTIFIER)
    writeLocation(pid.loc)
    writeSeq(pid.value)(writeString)
  }

  private def writeString(str: String): Unit = {
    val index = stringTable.intern(str)
    writer.writeVarInt(index)
  }

  private def writeURL(url: URL): Unit = {
    writeString(url.toExternalForm)
  }

  private def writeOption[T](opt: Option[T])(writeValue: T => Unit): Unit = {
    if opt.isDefined then
      writer.writeBoolean(true)
      writeValue(opt.get)
    else
      writer.writeBoolean(false)
    end if
  }

  private def writeSeq[T](seq: Seq[T])(writeElement: T => Unit): Unit = {
    writer.writeVarInt(seq.length)
    seq.foreach(writeElement)
  }

  private def writeContents[T <: RiddlValue](contents: Contents[T]): Unit = {
    writer.writeVarInt(contents.length)
    // Note: Individual elements are written by the main process() method during traversal
  }

  override def close(): Unit = ()
}
