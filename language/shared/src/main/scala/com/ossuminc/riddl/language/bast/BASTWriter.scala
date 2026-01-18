/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.URL
import wvlet.airframe.ulid.ULID

/** BAST serialization utility class
  *
  * Contains all the serialization methods for AST nodes. This class does NOT
  * perform traversal - it only knows HOW to serialize individual nodes. The
  * actual traversal is performed by BASTWriterPass in the passes module,
  * which uses the Pass framework to ensure correct node ordering.
  *
  * This separation allows:
  * - Serialization logic to live in the language module (no circular dependency)
  * - Traversal to use the battle-tested Pass framework
  * - Independent testing of serialization methods
  *
  * @param writer The byte buffer to write to
  * @param stringTable The string interning table
  */
class BASTWriter(val writer: ByteBufferWriter, val stringTable: StringTable) {

  private var lastLocation: At = At.empty
  private var firstLocationWritten: Boolean = false
  private var currentSourcePath: String = ""
  private var nodeCount: Int = 0

  /** Get the total number of nodes written */
  def getNodeCount: Int = nodeCount

  /** Write a node tag with optional metadata flag
    *
    * Phase 7 optimization: Uses high bit of tag to indicate metadata presence.
    * If hasMetadata is true, sets bit 7 (0x80) to indicate metadata follows.
    *
    * @param tag The base node type tag (0-127)
    * @param hasMetadata Whether this node has non-empty metadata
    */
  def writeNodeTag(tag: Byte, hasMetadata: Boolean): Unit = {
    if hasMetadata then
      // Use & 0xFF to treat bytes as unsigned, avoiding negative values
      writer.writeU8((tag & 0xFF) | 0x80)
    else
      writer.writeU8(tag & 0xFF)
  }

  /** Reset location tracking (called at start of serialization) */
  def resetLocationTracking(): Unit = {
    lastLocation = At.empty
    firstLocationWritten = false
    currentSourcePath = ""
  }

  /** Write a FILE_CHANGE_MARKER if the source file changed.
    * Call this before writing any node to handle source file transitions.
    * @param loc The location of the node being written
    */
  private def writeSourceChangeIfNeeded(loc: At): Unit = {
    val origin = loc.source.origin
    // For the first node, always write the marker to establish the initial source
    // For subsequent nodes, skip if origin is "empty" (synthetic locations use same source)
    if currentSourcePath.isEmpty then
      // First node - always write marker to establish source
      writer.writeU8(FILE_CHANGE_MARKER)
      writeString(if origin == "empty" then "" else origin)
      currentSourcePath = if origin == "empty" then "" else origin
    else if origin != "empty" && origin != currentSourcePath then
      // Source changed - write marker
      writer.writeU8(FILE_CHANGE_MARKER)
      writeString(origin)
      currentSourcePath = origin
    end if
  }

  /** Reserve space for header at the beginning of the buffer */
  def reserveHeader(): Unit = {
    writer.writeRawBytes(new Array[Byte](HEADER_SIZE))
  }

  /** Write the string table to the buffer
    * @return The offset where the string table was written
    */
  def writeStringTable(): Int = {
    val offset = writer.position
    stringTable.writeTo(writer)
    offset
  }

  /** Finalize the BAST output by writing the header
    * @param stringTableOffset The offset of the string table
    * @return The final bytes including header
    */
  def finalize(stringTableOffset: Int): Array[Byte] = {
    val bytes = writer.toByteArray
    val checksum = BinaryFormat.calculateChecksum(bytes, HEADER_SIZE, bytes.length - HEADER_SIZE)

    val header = BinaryFormat.Header(
      magic = MAGIC_BYTES,
      version = VERSION,
      flags = 0,
      stringTableOffset = stringTableOffset,
      rootOffset = HEADER_SIZE,
      fileSize = writer.size,
      checksum = checksum,
      reserved = Array.fill(4)(0.toByte)
    )

    // Write header at the beginning
    val headerBytes = BinaryFormat.serializeHeader(header)
    val finalBytes = writer.toByteArray
    System.arraycopy(headerBytes, 0, finalBytes, 0, HEADER_SIZE)
    finalBytes
  }

  // ========== Main Dispatch Method ==========

  /** Write a single AST node (dispatches to appropriate write method)
    * @param value The node to write
    */
  def writeNode(value: RiddlValue): Unit = {
    // Check if source file changed - write marker before the node tag
    writeSourceChangeIfNeeded(value.loc)
    nodeCount += 1
    value match {
      // Root containers
      case n: Nebula => writeNebula(n)
      case r: Root => writeRoot(r)
      case i: Include[?] => writeInclude(i)
      case bi: BASTImport => writeBASTImport(bi)

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

      // Statements (10 declarative statements per riddlsim spec)
      case s: PromptStatement => writePromptStatement(s)
      case s: ErrorStatement => writeErrorStatement(s)
      case s: SetStatement => writeSetStatement(s)
      case s: SendStatement => writeSendStatement(s)
      case s: MorphStatement => writeMorphStatement(s)
      case s: BecomeStatement => writeBecomeStatement(s)
      case s: TellStatement => writeTellStatement(s)
      case s: WhenStatement => writeWhenStatement(s)
      case s: MatchStatement => writeMatchStatement(s)
      case s: LetStatement => writeLetStatement(s)
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
        println(s"Unhandled node type in BASTWriter: ${value.getClass.getSimpleName} at ${value.loc}")
    }
  }

  // ========== Root Container Serialization ==========

  def writeRoot(r: Root): Unit = {
    writer.writeU8(NODE_NEBULA) // Root uses same tag as Nebula
    writeLocation(r.loc)
    writeIdentifier(Identifier.empty) // Root has no id
    writeContents(r.contents)
    // Metadata for Root is always empty, so no need to store it
  }

  def writeNebula(n: Nebula): Unit = {
    writer.writeU8(NODE_NEBULA)
    writeLocation(n.loc)
    writeIdentifier(Identifier.empty) // Nebula has no explicit id field
    writeContents(n.contents)
  }

  def writeInclude[T <: RiddlValue](i: Include[T]): Unit = {
    writer.writeU8(NODE_INCLUDE)
    writeLocation(i.loc)
    writeURL(i.origin)
    writeContents(i.contents)
  }

  def writeBASTImport(bi: BASTImport): Unit = {
    writer.writeU8(NODE_BAST_IMPORT)
    writeLocation(bi.loc)
    writeLiteralString(bi.path)
    // Contents are not serialized - they're loaded dynamically
    // by BASTLoader when this import is encountered
  }

  // ========== Definition Serialization ==========

  def writeDomain(d: Domain): Unit = {
    writeNodeTag(NODE_DOMAIN, d.metadata.nonEmpty)
    writeLocation(d.loc)
    writeIdentifierInline(d.id)  // Inline - no tag needed
    writeContents(d.contents)
    // Metadata written by traverse() if flag is set
  }

  def writeContext(c: Context): Unit = {
    writeNodeTag(NODE_CONTEXT, c.metadata.nonEmpty)
    writeLocation(c.loc)
    writeIdentifierInline(c.id)  // Inline - no tag needed
    writeContents(c.contents)
  }

  def writeEntity(e: Entity): Unit = {
    writeNodeTag(NODE_ENTITY, e.metadata.nonEmpty)
    writeLocation(e.loc)
    writeIdentifierInline(e.id)  // Inline - no tag needed
    writeContents(e.contents)
  }

  def writeModule(m: Module): Unit = {
    writeNodeTag(NODE_MODULE, m.metadata.nonEmpty)
    writeLocation(m.loc)
    writeIdentifierInline(m.id)  // Inline - no tag needed
    writeContents(m.contents)
  }

  def writeType(t: Type): Unit = {
    writeNodeTag(NODE_TYPE, t.metadata.nonEmpty)
    writeLocation(t.loc)
    writeIdentifierInline(t.id)  // Inline - no tag needed
    writeTypeExpression(t.typEx)
  }

  def writeFunction(f: Function): Unit = {
    writeNodeTag(NODE_FUNCTION, f.metadata.nonEmpty)
    writeLocation(f.loc)
    writeIdentifierInline(f.id)  // Inline - no tag needed
    writeOption(f.input)((agg: Aggregation) => writeTypeExpression(agg))
    writeOption(f.output)((agg: Aggregation) => writeTypeExpression(agg))
    writeContents(f.contents)
  }

  def writeAdaptor(a: Adaptor): Unit = {
    writeNodeTag(NODE_ADAPTOR, a.metadata.nonEmpty)
    writeLocation(a.loc)
    writeIdentifierInline(a.id)  // Inline - no tag needed
    a.direction match {
      case _: InboundAdaptor => writer.writeU8(ADAPTOR_INBOUND)
      case _: OutboundAdaptor => writer.writeU8(ADAPTOR_OUTBOUND)
    }
    writeContextRef(a.referent)
    writeContents(a.contents)
  }

  def writeSaga(s: Saga): Unit = {
    writeNodeTag(NODE_SAGA, s.metadata.nonEmpty)
    writeLocation(s.loc)
    writeIdentifierInline(s.id)  // Inline - no tag needed
    writeOption(s.input)((agg: Aggregation) => writeTypeExpression(agg))
    writeOption(s.output)((agg: Aggregation) => writeTypeExpression(agg))
    writeContents(s.contents)
  }

  def writeProjector(p: Projector): Unit = {
    writeNodeTag(NODE_PROJECTOR, p.metadata.nonEmpty)
    writeLocation(p.loc)
    writeIdentifierInline(p.id)  // Inline - no tag needed
    writeContents(p.contents)
  }

  def writeRepository(r: Repository): Unit = {
    writeNodeTag(NODE_REPOSITORY, r.metadata.nonEmpty)
    writeLocation(r.loc)
    writeIdentifierInline(r.id)  // Inline - no tag needed
    writeContents(r.contents)
  }

  def writeStreamlet(s: Streamlet): Unit = {
    writeNodeTag(NODE_STREAMLET, s.metadata.nonEmpty)
    writeLocation(s.loc)
    writeIdentifierInline(s.id)  // Inline - no tag needed
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

  def writeEpic(e: Epic): Unit = {
    writeNodeTag(NODE_EPIC, e.metadata.nonEmpty)
    writeLocation(e.loc)
    writeIdentifierInline(e.id)  // Inline - no tag needed
    writeUserStory(e.userStory)
    writeContents(e.contents)
  }

  // ========== Leaf Definition Serialization ==========

  def writeAuthor(a: Author): Unit = {
    writeNodeTag(NODE_AUTHOR, a.metadata.nonEmpty)
    writeLocation(a.loc)
    writeIdentifierInline(a.id)  // Inline - no tag needed
    writeLiteralString(a.name)
    writeLiteralString(a.email)
    writeOption(a.organization)(writeLiteralString)
    writeOption(a.title)(writeLiteralString)
    writeOption(a.url)(writeURL)
  }

  def writeUser(u: User): Unit = {
    writeNodeTag(NODE_USER, u.metadata.nonEmpty)
    writeLocation(u.loc)
    writeIdentifier(u.id)  // Keep tag to distinguish from UserRef/UserStory
    writeLiteralString(u.is_a)
  }

  def writeTerm(t: Term): Unit = {
    // Term has no metadata field, use simple tag
    writer.writeU8(NODE_TERM)
    writeLocation(t.loc)
    writeIdentifierInline(t.id)  // Inline - no tag needed
    writeSeq(t.definition)(writeLiteralString)
  }

  def writeRelationship(r: Relationship): Unit = {
    writeNodeTag(NODE_PIPE, r.metadata.nonEmpty) // Reusing PIPE tag for relationship
    writeLocation(r.loc)
    writeIdentifierInline(r.id)  // Inline - no tag needed
    writeProcessorRef(r.withProcessor)
    writer.writeU8(r.cardinality.ordinal.toByte)
    writeOption(r.label)(writeLiteralString)
  }

  def writeConstant(c: Constant): Unit = {
    writeNodeTag(NODE_FIELD, c.metadata.nonEmpty) // Constants similar to fields
    writeLocation(c.loc)
    writeIdentifierInline(c.id)  // Inline - no tag needed
    writeTypeExpression(c.typeEx)
    writeLiteralString(c.value)
  }

  // ========== Type Component Serialization ==========

  def writeField(f: Field): Unit = {
    writeNodeTag(NODE_FIELD, f.metadata.nonEmpty)
    writeLocation(f.loc)
    writeIdentifier(f.id)  // Keep tag to distinguish from MethodArgument
    writeTypeExpression(f.typeEx)
  }

  def writeEnumerator(e: Enumerator): Unit = {
    writeNodeTag(NODE_ENUMERATOR, e.metadata.nonEmpty)
    writeLocation(e.loc)
    writeIdentifierInline(e.id)  // Inline - no tag needed
    writeOption(e.enumVal)((v: Long) => writer.writeVarLong(v))
  }

  def writeMethod(m: Method): Unit = {
    writeNodeTag(NODE_FIELD, m.metadata.nonEmpty) // Methods similar to fields
    writeLocation(m.loc)
    writeIdentifierInline(m.id)  // Inline - no tag needed
    writeTypeExpression(m.typeEx)
    writeSeq(m.args)(writeMethodArgument)
  }

  def writeMethodArgument(a: MethodArgument): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(a.loc)
    writeString(a.name)
    writeTypeExpression(a.typeEx)
  }

  // ========== Handler Component Serialization ==========

  def writeHandler(h: Handler): Unit = {
    writeNodeTag(NODE_HANDLER, h.metadata.nonEmpty)
    writeLocation(h.loc)
    writeIdentifierInline(h.id)  // Inline - no tag needed
    writeContents(h.contents)
  }

  def writeState(s: State): Unit = {
    writeNodeTag(NODE_STATE, s.metadata.nonEmpty)
    writeLocation(s.loc)
    writeIdentifierInline(s.id)  // Inline - no tag needed
    writeTypeRefInline(s.typ)    // Inline - position known
  }

  def writeInvariant(i: Invariant): Unit = {
    writeNodeTag(NODE_INVARIANT, i.metadata.nonEmpty)
    writeLocation(i.loc)
    writeIdentifierInline(i.id)  // Inline - no tag needed
    writeOption(i.condition)(writeLiteralString)
  }

  // ========== OnClause Serialization ==========

  def writeOnInitializationClause(oc: OnInitializationClause): Unit = {
    writeNodeTag(NODE_ON_CLAUSE, oc.metadata.nonEmpty)
    writer.writeU8(0) // Init clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  def writeOnTerminationClause(oc: OnTerminationClause): Unit = {
    writeNodeTag(NODE_ON_CLAUSE, oc.metadata.nonEmpty)
    writer.writeU8(1) // Term clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  def writeOnMessageClause(oc: OnMessageClause): Unit = {
    writeNodeTag(NODE_ON_CLAUSE, oc.metadata.nonEmpty)
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

  def writeOnOtherClause(oc: OnOtherClause): Unit = {
    writeNodeTag(NODE_ON_CLAUSE, oc.metadata.nonEmpty)
    writer.writeU8(3) // Other clause type
    writeLocation(oc.loc)
    writeContents(oc.contents)
  }

  // ========== Streamlet Component Serialization ==========

  def writeInlet(i: Inlet): Unit = {
    writeNodeTag(NODE_INLET, i.metadata.nonEmpty)
    writeLocation(i.loc)
    writeIdentifierInline(i.id)  // Inline - no tag needed
    writeTypeRefInline(i.type_)  // Inline - position known
  }

  def writeOutlet(o: Outlet): Unit = {
    writeNodeTag(NODE_OUTLET, o.metadata.nonEmpty)
    writeLocation(o.loc)
    writeIdentifier(o.id)  // Keep tag to distinguish from ShownBy
    writeTypeRefInline(o.type_)  // Inline - position known
  }

  def writeConnector(c: Connector): Unit = {
    writeNodeTag(NODE_CONNECTOR, c.metadata.nonEmpty)
    writeLocation(c.loc)
    writeIdentifierInline(c.id)  // Inline - no tag needed
    writeOutletRef(c.from)
    writeInletRef(c.to)
  }

  // ========== Repository Component Serialization ==========

  def writeSchema(s: Schema): Unit = {
    writeNodeTag(NODE_SCHEMA, s.metadata.nonEmpty)
    writer.writeU8(s.schemaKind.ordinal) // Subtype: 0=Relational, 1=Document, 2=Graphical
    writeLocation(s.loc)
    writeIdentifierInline(s.id)  // Inline - no tag needed
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

  def writeUseCase(uc: UseCase): Unit = {
    writeNodeTag(NODE_EPIC, uc.metadata.nonEmpty) // UseCase similar to Epic
    writeLocation(uc.loc)
    writeIdentifierInline(uc.id)  // Inline - no tag needed
    writeUserStory(uc.userStory)
    writeContents(uc.contents)
  }

  def writeUserStory(us: UserStory): Unit = {
    // UserStory has no metadata field, use simple tag
    writer.writeU8(NODE_USER) // User story relates to user
    writeLocation(us.loc)
    writeUserRef(us.user)
    writeLiteralString(us.capability)
    writeLiteralString(us.benefit)
  }

  def writeShownBy(sb: ShownBy): Unit = {
    // ShownBy has no metadata field, use simple tag
    writer.writeU8(NODE_OUTLET) // Reusing outlet tag
    writeLocation(sb.loc)
    writeSeq(sb.urls)(writeURL)
  }

  def writeSagaStep(ss: SagaStep): Unit = {
    writeNodeTag(NODE_SAGA_STEP, ss.metadata.nonEmpty)
    writeLocation(ss.loc)
    writeIdentifierInline(ss.id)  // Inline - no tag needed
    // NOTE: doStatements and undoStatements are written by the Pass's traverse() override
    // to properly interleave count-items, count-items
  }

  // ========== Interaction Serialization ==========

  def writeParallelInteractions(pi: ParallelInteractions): Unit = {
    writeNodeTag(NODE_PIPE, pi.metadata.nonEmpty) // Interactions like connectors
    writer.writeU8(0) // Parallel type
    writeLocation(pi.loc)
    writeContents(pi.contents)
  }

  def writeSequentialInteractions(si: SequentialInteractions): Unit = {
    writeNodeTag(NODE_PIPE, si.metadata.nonEmpty)
    writer.writeU8(1) // Sequential type
    writeLocation(si.loc)
    writeContents(si.contents)
  }

  def writeOptionalInteractions(oi: OptionalInteractions): Unit = {
    writeNodeTag(NODE_PIPE, oi.metadata.nonEmpty)
    writer.writeU8(2) // Optional type
    writeLocation(oi.loc)
    writeContents(oi.contents)
  }

  def writeVagueInteraction(vi: VagueInteraction): Unit = {
    writeNodeTag(NODE_PIPE, vi.metadata.nonEmpty)
    writer.writeU8(10) // Vague interaction type
    writeLocation(vi.loc)
    writeLiteralString(vi.from)
    writeLiteralString(vi.relationship)
    writeLiteralString(vi.to)
  }

  def writeSendMessageInteraction(smi: SendMessageInteraction): Unit = {
    writeNodeTag(NODE_PIPE, smi.metadata.nonEmpty)
    writer.writeU8(11) // Send message interaction
    writeLocation(smi.loc)
    writeReference(smi.from)
    writeMessageRef(smi.message)
    writeProcessorRef(smi.to)
  }

  def writeArbitraryInteraction(ai: ArbitraryInteraction): Unit = {
    writeNodeTag(NODE_PIPE, ai.metadata.nonEmpty)
    writer.writeU8(12) // Arbitrary interaction
    writeLocation(ai.loc)
    writeReference(ai.from)
    writeLiteralString(ai.relationship)
    writeReference(ai.to)
  }

  def writeSelfInteraction(si: SelfInteraction): Unit = {
    writeNodeTag(NODE_PIPE, si.metadata.nonEmpty)
    writer.writeU8(13) // Self interaction
    writeLocation(si.loc)
    writeReference(si.from)
    writeLiteralString(si.relationship)
  }

  def writeFocusOnGroupInteraction(fgi: FocusOnGroupInteraction): Unit = {
    writeNodeTag(NODE_PIPE, fgi.metadata.nonEmpty)
    writer.writeU8(14) // Focus on group
    writeLocation(fgi.loc)
    writeUserRef(fgi.from)
    writeGroupRef(fgi.to)
  }

  def writeDirectUserToURLInteraction(dui: DirectUserToURLInteraction): Unit = {
    writeNodeTag(NODE_PIPE, dui.metadata.nonEmpty)
    writer.writeU8(15) // Direct to URL
    writeLocation(dui.loc)
    writeUserRef(dui.from)
    writeURL(dui.url)
  }

  def writeShowOutputInteraction(soi: ShowOutputInteraction): Unit = {
    writeNodeTag(NODE_PIPE, soi.metadata.nonEmpty)
    writer.writeU8(16) // Show output
    writeLocation(soi.loc)
    writeOutputRef(soi.from)
    writeLiteralString(soi.relationship)
    writeUserRef(soi.to)
  }

  def writeSelectInputInteraction(sii: SelectInputInteraction): Unit = {
    writeNodeTag(NODE_PIPE, sii.metadata.nonEmpty)
    writer.writeU8(17) // Select input
    writeLocation(sii.loc)
    writeUserRef(sii.from)
    writeInputRef(sii.to)
  }

  def writeTakeInputInteraction(tii: TakeInputInteraction): Unit = {
    writeNodeTag(NODE_PIPE, tii.metadata.nonEmpty)
    writer.writeU8(18) // Take input
    writeLocation(tii.loc)
    writeUserRef(tii.from)
    writeInputRef(tii.to)
  }

  // ========== UI Component Serialization ==========

  def writeGroup(g: Group): Unit = {
    writeNodeTag(NODE_GROUP, g.metadata.nonEmpty)
    writeLocation(g.loc)
    writeString(g.alias)
    writeIdentifier(g.id)  // Keep tag to distinguish from ContainedGroup
    writeContents(g.contents)
  }

  def writeContainedGroup(cg: ContainedGroup): Unit = {
    writeNodeTag(NODE_GROUP, cg.metadata.nonEmpty)
    writeLocation(cg.loc)
    writeIdentifier(cg.id)  // Keep tag to distinguish from Group
    writeGroupRef(cg.group)
  }

  def writeInput(i: Input): Unit = {
    writeNodeTag(NODE_INPUT, i.metadata.nonEmpty)
    writeLocation(i.loc)
    writeString(i.nounAlias)
    writeIdentifierInline(i.id)  // Inline - no tag needed
    writeString(i.verbAlias)
    writeTypeRefInline(i.takeIn)  // Inline - position known
    writeContents(i.contents)
  }

  def writeOutput(o: Output): Unit = {
    writeNodeTag(NODE_OUTPUT, o.metadata.nonEmpty)
    writeLocation(o.loc)
    writeString(o.nounAlias)
    writeIdentifierInline(o.id)  // Inline - no tag needed
    writeString(o.verbAlias)
    // Handle union type: TypeRef | ConstantRef | LiteralString
    o.putOut match {
      case tr: TypeRef =>
        writer.writeU8(0)
        writeTypeRefInline(tr)  // Inline - discriminator identifies type
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
  // 10 declarative statements per riddlsim specification:
  // send, tell, morph, become, when, match, error, let, set, arbitrary
  // Phase 7: Statements use NODE_STATEMENT tag to distinguish from handlers

  def writePromptStatement(s: PromptStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(0) // Prompt statement type
    writeLocation(s.loc)
    writeLiteralString(s.what)
  }

  def writeErrorStatement(s: ErrorStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(1) // Error statement
    writeLocation(s.loc)
    writeLiteralString(s.message)
  }

  def writeSetStatement(s: SetStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(3) // Set statement
    writeLocation(s.loc)
    writeFieldRef(s.field)
    writeLiteralString(s.value)
  }

  def writeSendStatement(s: SendStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(5) // Send statement
    writeLocation(s.loc)
    writeMessageRef(s.msg)
    writePortletRef(s.portlet)
  }

  def writeMorphStatement(s: MorphStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(7) // Morph statement
    writeLocation(s.loc)
    writeEntityRef(s.entity)
    writeStateRef(s.state)
    writeMessageRef(s.value)
  }

  def writeBecomeStatement(s: BecomeStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(8) // Become statement
    writeLocation(s.loc)
    writeEntityRef(s.entity)
    writeHandlerRef(s.handler)
  }

  def writeTellStatement(s: TellStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(9) // Tell statement
    writeLocation(s.loc)
    writeMessageRef(s.msg)
    writeProcessorRef(s.processorRef)
  }

  def writeWhenStatement(s: WhenStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(10) // When statement
    writeLocation(s.loc)
    writeLiteralString(s.condition)
    // NOTE: thenStatements count/items are written by the Pass's traverse() override
  }

  def writeMatchStatement(s: MatchStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(11) // Match statement
    writeLocation(s.loc)
    writeLiteralString(s.expression)
    // Write cases
    writer.writeVarInt(s.cases.size)
    s.cases.foreach { mc =>
      writeLocation(mc.loc)
      writeLiteralString(mc.pattern)
      writeContents(mc.statements)
    }
    // Write default
    writeContents(s.default)
    // NOTE: case and default statement items are written by the Pass's traverse() override
  }

  def writeLetStatement(s: LetStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(12) // Let statement
    writeLocation(s.loc)
    writeIdentifier(s.identifier)
    writeLiteralString(s.expression)
  }

  def writeCodeStatement(s: CodeStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(13) // Code statement
    writeLocation(s.loc)
    writeLiteralString(s.language)
    writeString(s.body)
  }

  // ========== Reference Serialization ==========

  def writeAuthorRef(r: AuthorRef): Unit = {
    writer.writeU8(NODE_AUTHOR)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeTypeRef(r: TypeRef): Unit = {
    writer.writeU8(NODE_TYPE)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  /** Write TypeRef without tag - used when type is known from position */
  def writeTypeRefInline(r: TypeRef): Unit = {
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  def writeFieldRef(r: FieldRef): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeConstantRef(r: ConstantRef): Unit = {
    writer.writeU8(NODE_FIELD)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeCommandRef(r: CommandRef): Unit = {
    writer.writeU8(NODE_COMMAND_REF)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeEventRef(r: EventRef): Unit = {
    writer.writeU8(NODE_EVENT_REF)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeQueryRef(r: QueryRef): Unit = {
    writer.writeU8(NODE_QUERY_REF)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeResultRef(r: ResultRef): Unit = {
    writer.writeU8(NODE_RESULT_REF)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeRecordRef(r: RecordRef): Unit = {
    writer.writeU8(NODE_RECORD_REF)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeAdaptorRef(r: AdaptorRef): Unit = {
    writer.writeU8(NODE_ADAPTOR)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeFunctionRef(r: FunctionRef): Unit = {
    writer.writeU8(NODE_FUNCTION)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeHandlerRef(r: HandlerRef): Unit = {
    writer.writeU8(NODE_HANDLER)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeStateRef(r: StateRef): Unit = {
    writer.writeU8(NODE_STATE)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeEntityRef(r: EntityRef): Unit = {
    writer.writeU8(NODE_ENTITY)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeRepositoryRef(r: RepositoryRef): Unit = {
    writer.writeU8(NODE_REPOSITORY)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeProjectorRef(r: ProjectorRef): Unit = {
    writer.writeU8(NODE_PROJECTOR)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeContextRef(r: ContextRef): Unit = {
    writer.writeU8(NODE_CONTEXT)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeStreamletRef(r: StreamletRef): Unit = {
    writer.writeU8(NODE_STREAMLET)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  def writeInletRef(r: InletRef): Unit = {
    writer.writeU8(NODE_INLET)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeOutletRef(r: OutletRef): Unit = {
    writer.writeU8(NODE_OUTLET)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeSagaRef(r: SagaRef): Unit = {
    writer.writeU8(NODE_SAGA)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeUserRef(r: UserRef): Unit = {
    writer.writeU8(NODE_USER)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeEpicRef(r: EpicRef): Unit = {
    writer.writeU8(NODE_EPIC)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  def writeGroupRef(r: GroupRef): Unit = {
    writer.writeU8(NODE_GROUP)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  def writeInputRef(r: InputRef): Unit = {
    writer.writeU8(NODE_INPUT)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  def writeOutputRef(r: OutputRef): Unit = {
    writer.writeU8(NODE_OUTPUT)
    writeLocation(r.loc)
    writeString(r.keyword)
    writePathIdentifierInline(r.pathId)
  }

  def writeDomainRef(r: DomainRef): Unit = {
    writer.writeU8(NODE_DOMAIN)
    writeLocation(r.loc)
    writePathIdentifierInline(r.pathId)
  }

  // Generic reference writer for polymorphic cases
  def writeReference(r: Reference[?]): Unit = {
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

  def writeMessageRef(r: MessageRef): Unit = {
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

  def writeProcessorRef(r: ProcessorRef[?]): Unit = {
    r match {
      case ar: AdaptorRef => writeAdaptorRef(ar)
      case er: EntityRef => writeEntityRef(er)
      case rr: RepositoryRef => writeRepositoryRef(rr)
      case pr: ProjectorRef => writeProjectorRef(pr)
      case cr: ContextRef => writeContextRef(cr)
      case sr: StreamletRef => writeStreamletRef(sr)
    }
  }

  def writePortletRef(r: PortletRef[?]): Unit = {
    r match {
      case ir: InletRef => writeInletRef(ir)
      case or: OutletRef => writeOutletRef(or)
    }
  }

  // ========== Metadata Serialization ==========

  def writeBriefDescription(bd: BriefDescription): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(0) // Brief type
    writeLocation(bd.loc)
    writeLiteralString(bd.brief)
  }

  def writeBlockDescription(bd: BlockDescription): Unit = {
    writer.writeU8(NODE_BLOCK_DESCRIPTION)
    writeLocation(bd.loc)
    writeSeq(bd.lines)(writeLiteralString)
  }

  def writeURLDescription(ud: URLDescription): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(2) // URL type
    writeLocation(ud.loc)
    writeURL(ud.url)
  }

  def writeLineComment(c: LineComment): Unit = {
    writer.writeU8(NODE_COMMENT)
    writeLocation(c.loc)
    writeString(c.text)
  }

  def writeInlineComment(c: InlineComment): Unit = {
    writer.writeU8(NODE_BLOCK_COMMENT)
    writeLocation(c.loc)
    writeSeq(c.lines)(writeString)
  }

  def writeOptionValue(ov: OptionValue): Unit = {
    writer.writeU8(NODE_DESCRIPTION) // Options are metadata
    writer.writeU8(10) // Option type
    writeLocation(ov.loc)
    writeString(ov.name)
    writeSeq(ov.args)(writeLiteralString)
  }

  // ========== Attachment Serialization ==========

  def writeFileAttachment(a: FileAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(20) // File attachment
    writeLocation(a.loc)
    writeIdentifier(a.id)
    writeString(a.mimeType)
    writeLiteralString(a.inFile)
  }

  def writeStringAttachment(a: StringAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(21) // String attachment
    writeLocation(a.loc)
    writeIdentifier(a.id)
    writeString(a.mimeType)
    writeLiteralString(a.value)
  }

  def writeULIDAttachment(a: ULIDAttachment): Unit = {
    writer.writeU8(NODE_DESCRIPTION)
    writer.writeU8(22) // ULID attachment
    writeLocation(a.loc)
    // Write ULID as bytes
    val ulidBytes = a.ulid.toBytes
    writer.writeRawBytes(ulidBytes)
  }

  // ========== Simple Value Serialization ==========

  def writeLiteralString(ls: LiteralString): Unit = {
    writer.writeU8(NODE_LITERAL_STRING)
    writeLocation(ls.loc)
    writeString(ls.s)
  }

  // ========== Streamlet Shape Serialization ==========

  def writeVoid(v: Void): Unit = {
    writer.writeU8(STREAMLET_VOID)
    writeLocation(v.loc)
  }

  def writeSource(s: Source): Unit = {
    writer.writeU8(STREAMLET_SOURCE)
    writeLocation(s.loc)
  }

  def writeSink(s: Sink): Unit = {
    writer.writeU8(STREAMLET_SINK)
    writeLocation(s.loc)
  }

  def writeFlow(f: Flow): Unit = {
    writer.writeU8(STREAMLET_FLOW)
    writeLocation(f.loc)
  }

  def writeMerge(m: Merge): Unit = {
    writer.writeU8(STREAMLET_MERGE)
    writeLocation(m.loc)
  }

  def writeSplit(s: Split): Unit = {
    writer.writeU8(STREAMLET_SPLIT)
    writeLocation(s.loc)
  }

  def writeRouter(r: Router): Unit = {
    writer.writeU8(STREAMLET_VOID) // Router not defined, use void
    writeLocation(r.loc)
  }

  // ========== Adaptor Direction Serialization ==========

  def writeInboundAdaptor(ia: InboundAdaptor): Unit = {
    writer.writeU8(ADAPTOR_INBOUND)
    writeLocation(ia.loc)
  }

  def writeOutboundAdaptor(oa: OutboundAdaptor): Unit = {
    writer.writeU8(ADAPTOR_OUTBOUND)
    writeLocation(oa.loc)
  }

  // ========== Container Serialization ==========

  def writeSimpleContainer(sc: SimpleContainer[?]): Unit = {
    writer.writeU8(NODE_NEBULA) // Containers like nebula
    writeLocation(sc.loc)
    writeContents(sc.contents)
  }

  // ========== Type Expression Serialization ==========

  def writeTypeExpression(te: TypeExpression): Unit = {
    te match {
      // Aggregate types
      case a: Aggregation =>
        writer.writeU8(TYPE_AGGREGATION)
        writer.writeU8(255) // Subtype marker for plain Aggregation (vs AggregateUseCaseTypeExpression)
        writeLocation(a.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(a.contents.length)
        a.contents.toSeq.foreach { item =>
          writeNode(item)
          // Phase 7: Only write metadata if non-empty (flag was set in tag)
          item match {
            case wm: WithMetaData if wm.metadata.nonEmpty => writeMetadataCount(wm.metadata)
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
          writeNode(item)
          // Phase 7: Only write metadata if non-empty (flag was set in tag)
          item match {
            case wm: WithMetaData if wm.metadata.nonEmpty => writeMetadataCount(wm.metadata)
            case _ => ()
          }
        }

      case e: EntityReferenceTypeExpression =>
        writer.writeU8(TYPE_REF)
        writer.writeU8(10) // Entity ref type
        writeLocation(e.loc)
        writePathIdentifierInline(e.entity)

      // Collection types
      case alt: Alternation =>
        writer.writeU8(TYPE_ALTERNATION)
        writeLocation(alt.loc)
        // Write count and items using writeTypeExpression (NOT writeNode - no FILE_CHANGE_MARKERs)
        writer.writeVarInt(alt.of.length)
        alt.of.toSeq.foreach { item =>
          writeTypeExpression(item)
        }

      case enumeration: Enumeration =>
        writer.writeU8(TYPE_ENUMERATION)
        writeLocation(enumeration.loc)
        // Write count and items inline (TypeExpressions are not traversed)
        writer.writeVarInt(enumeration.enumerators.length)
        enumeration.enumerators.toSeq.foreach { item =>
          writeNode(item)
          // Phase 7: Only write metadata if non-empty (flag was set in tag)
          if item.metadata.nonEmpty then writeMetadataCount(item.metadata)
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
        writePathIdentifierInline(ate.pathId)

      // Predefined types - String
      case s: String_ =>
        // Phase 7 optimization: Use predefined tag for String with no min/max
        if s.min.isEmpty && s.max.isEmpty then
          writer.writeU8(TYPE_STRING_DEFAULT)
          writeLocation(s.loc)
        else
          writer.writeU8(TYPE_STRING)
          writer.writeU8(0) // String_ subtype (0 = plain String, 1 = URI, 2 = Blob)
          writeLocation(s.loc)
          writeOption(s.min)((v: Long) => writer.writeVarLong(v))
          writeOption(s.max)((v: Long) => writer.writeVarLong(v))
        end if

      case p: Pattern =>
        writer.writeU8(TYPE_PATTERN)
        writeLocation(p.loc)
        writeSeq(p.pattern)(writeLiteralString)

      case u: UniqueId =>
        writer.writeU8(TYPE_UNIQUE_ID)
        writer.writeU8(0) // UniqueId subtype (0 = UniqueId, 1 = UUID, 2 = UserId)
        writeLocation(u.loc)
        writePathIdentifierInline(u.entityPath)

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
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_INTEGER)
        writeLocation(i.loc)

      case w: Whole =>
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_WHOLE)
        writeLocation(w.loc)

      case n: Natural =>
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_NATURAL)
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
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_REAL)
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

      // Time types - Phase 7 optimization: Predefined tags save subtype byte
      case d: Date =>
        writer.writeU8(TYPE_DATE)
        writeLocation(d.loc)

      case t: Time =>
        writer.writeU8(TYPE_TIME)
        writeLocation(t.loc)

      case dt: DateTime =>
        writer.writeU8(TYPE_DATETIME)
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
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_TIMESTAMP)
        writeLocation(ts.loc)

      case d: Duration =>
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_DURATION)
        writeLocation(d.loc)

      // Other predefined types
      case u: UUID =>
        // Phase 7 optimization: Predefined tag saves subtype byte
        writer.writeU8(TYPE_UUID)
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

  def writeLocation(loc: At): Unit = {
    // Optimized location encoding (Phase 7):
    // - Source file changes are handled by FILE_CHANGE_MARKER before node tags
    // - Locations just store offset deltas (no flag byte needed)
    // - Use zigzag encoding for signed deltas
    if !firstLocationWritten then
      // First location: write absolute offsets
      writer.writeVarInt(loc.offset)
      writer.writeVarInt(loc.endOffset)
      firstLocationWritten = true
    else
      // Subsequent locations: write deltas using zigzag encoding
      val offsetDelta = loc.offset - lastLocation.offset
      val endOffsetDelta = loc.endOffset - lastLocation.endOffset
      writer.writeZigzagInt(offsetDelta)
      writer.writeZigzagInt(endOffsetDelta)
    end if

    // Always update lastLocation to stay in sync with reader
    lastLocation = loc
  }

  def writeIdentifier(id: Identifier): Unit = {
    writer.writeU8(NODE_IDENTIFIER)
    writeLocation(id.loc)
    writeString(id.value)
  }

  /** Write identifier without tag - used when identifier position is known (e.g., after definition tag) */
  def writeIdentifierInline(id: Identifier): Unit = {
    writeLocation(id.loc)
    writeString(id.value)
  }

  def writePathIdentifier(pid: PathIdentifier): Unit = {
    writer.writeU8(NODE_PATH_IDENTIFIER)
    writeLocation(pid.loc)
    writeSeq(pid.value)(writeString)
  }

  /** Write PathIdentifier without tag - position is always known within references */
  def writePathIdentifierInline(pid: PathIdentifier): Unit = {
    writeLocation(pid.loc)
    writeSeq(pid.value)(writeString)
  }

  def writeString(str: String): Unit = {
    val index = stringTable.intern(str)
    writer.writeVarInt(index)
  }

  def writeURL(url: URL): Unit = {
    writeString(url.toExternalForm)
  }

  def writeOption[T](opt: Option[T])(writeValue: T => Unit): Unit = {
    if opt.isDefined then
      writer.writeBoolean(true)
      writeValue(opt.get)
    else
      writer.writeBoolean(false)
    end if
  }

  def writeSeq[T](seq: Seq[T])(writeElement: T => Unit): Unit = {
    writer.writeVarInt(seq.length)
    seq.foreach(writeElement)
  }

  def writeContents[T <: RiddlValue](contents: Contents[T]): Unit = {
    writer.writeVarInt(contents.length)
    // Note: Individual elements are written by the Pass's traverse() method
  }

  /** Write metadata count and items (called by BASTWriterPass during traversal) */
  def writeMetadataCount(metadata: Contents[MetaData]): Unit = {
    writer.writeVarInt(metadata.length)
    // Write each metadata item inline
    metadata.toSeq.foreach { item =>
      writeNode(item)
    }
  }
}

/** Companion object for BASTWriter */
object BASTWriter {
  /** Create a new BASTWriter with fresh buffers */
  def apply(): BASTWriter = {
    new BASTWriter(new ByteBufferWriter(), StringTable())
  }

  /** Create a BASTWriter with provided buffers */
  def apply(writer: ByteBufferWriter, stringTable: StringTable): BASTWriter = {
    new BASTWriter(writer, stringTable)
  }
}
