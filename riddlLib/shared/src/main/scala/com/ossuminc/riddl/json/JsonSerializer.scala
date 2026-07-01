/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, toSeq}

/** Pure, Native-safe serialization of a RIDDL [[AST.Root]] to the JSON wire
  * model ([[JsonModel]]) — the symmetric inverse of [[JsonAstBuilder]]. No I/O.
  *
  * `parseJson(root2Json(root))` re-validates identically for any model in the
  * documented JSON subset (see JSON_INPUT.md). It is **lossless for that
  * subset** and **best-effort (lossy, non-crashing) beyond it**: constructs the
  * schema cannot express (e.g. context-level invariants, statement kinds beyond
  * those modeled) are omitted rather than failing.
  */
object JsonSerializer:

  import JsonModel.*

  def serialize(root: Root): RootDto =
    val domains = root.contents.toSeq.collect { case d: Domain => serializeDomain(d) }
    val modules = root.contents.toSeq.collect { case m: Module => serializeModule(m) }
    RootDto(domains, modules)

  // ---------------------------------------------------------------------------
  // Small helpers
  // ---------------------------------------------------------------------------

  private def path(pid: PathIdentifier): String = pid.value.mkString(".")

  private def briefOf(md: Contents[MetaData]): Option[String] =
    // Widen to RiddlValue so matching individual union members doesn't trip a
    // spurious union-exhaustivity warning.
    val items: Seq[RiddlValue] = md.toSeq
    items.collectFirst { case b: BriefDescription => b.brief.s }

  private def metaOf(md: Contents[MetaData]): Option[MetaDto] =
    val items: Seq[RiddlValue] = md.toSeq
    val descr = items.collect { case d: BlockDescription => d.lines.map(_.s) }.flatten
    val terms = items.collect { case t: Term => TermDto(t.id.value, t.definition.map(_.s)) }
    val options = items.collect { case o: OptionValue => OptionDto(o.name, o.args.map(_.s)) }
    val authors = items.collect { case a: AuthorRef => path(a.pathId) }
    val attachments = items.collect {
      case fa: FileAttachment   => AttachmentDto(fa.id.value, fa.mimeType, fa.inFile.s, inFile = true)
      case sa: StringAttachment => AttachmentDto(sa.id.value, sa.mimeType, sa.value.s, inFile = false)
    }
    val comments = items.collect { case c: LineComment => c.text }
    if descr.isEmpty && terms.isEmpty && options.isEmpty && authors.isEmpty && attachments.isEmpty &&
      comments.isEmpty
    then None
    else Some(MetaDto(descr, terms, options, authors, attachments, comments))

  private def messageRefDto(mr: MessageRef): MessageRefDto = mr match
    case CommandRef(_, p) => MessageRefDto(path(p), "command")
    case EventRef(_, p)   => MessageRefDto(path(p), "event")
    case QueryRef(_, p)   => MessageRefDto(path(p), "query")
    case ResultRef(_, p)  => MessageRefDto(path(p), "result")
    case RecordRef(_, p)  => MessageRefDto(path(p), "record")
    case other            => MessageRefDto(path(other.pathId), "command")

  /** A processor reference -> (path, kind). */
  private def processorRef(pr: ProcessorRef[?]): (String, String) = pr match
    case EntityRef(_, p)     => (path(p), "entity")
    case ContextRef(_, p)    => (path(p), "context")
    case ProjectorRef(_, p)  => (path(p), "projector")
    case RepositoryRef(_, p) => (path(p), "repository")
    case AdaptorRef(_, p)    => (path(p), "adaptor")
    case other               => (path(other.pathId), "entity")

  private def portletRef(pr: PortletRef[?]): (String, String) = pr match
    case InletRef(_, p)  => (path(p), "inlet")
    case OutletRef(_, p) => (path(p), "outlet")

  private def refDto(r: Reference[?]): RefDto = r match
    case UserRef(_, p)       => RefDto("user", path(p))
    case EntityRef(_, p)     => RefDto("entity", path(p))
    case ContextRef(_, p)    => RefDto("context", path(p))
    case GroupRef(_, _, p)   => RefDto("group", path(p))
    case OutputRef(_, _, p)  => RefDto("output", path(p))
    case InputRef(_, _, p)   => RefDto("input", path(p))
    case AdaptorRef(_, p)    => RefDto("adaptor", path(p))
    case ProjectorRef(_, p)  => RefDto("projector", path(p))
    case other               => RefDto("user", path(other.pathId))

  // ---------------------------------------------------------------------------
  // Type expressions
  // ---------------------------------------------------------------------------

  private def serializeTypeExpr(te: TypeExpression): TypeExprDto = te match
    case String_(_, min, max)                 => StringDto(min, max)
    case UniqueId(_, p)                        => IdDto(Some(path(p)))
    case Currency(_, c)                        => CurrencyDto(Some(c))
    case Pattern(_, ps)                        => PatternDto(ps.map(_.s))
    case URI(_, scheme)                        => URIDto(scheme.map(_.s))
    case Blob(_, bk)                           => BlobDto(Some(bk.toString))
    case ZonedDate(_, z)                       => ZonedDto("ZonedDate", z.map(_.s))
    case ZonedDateTime(_, z)                   => ZonedDto("ZonedDateTime", z.map(_.s))
    case _: Bool                              => PredefDto("Boolean")
    case Decimal(_, w, f)                     => DecimalDto(Some(w), Some(f))
    case RangeType(_, mn, mx)                 => RangeDto(Some(mn), Some(mx))
    case Enumeration(_, es) =>
      EnumDto(es.toSeq.map(e => EnumeratorDto(e.id.value, e.enumVal)))
    case Alternation(_, of) => AlternationDto(of.toSeq.map(a => path(a.pathId)))
    case AliasedTypeExpression(_, _, p)      => AliasDto(path(p))
    case Sequence(_, of)                      => CollectionDto("Sequence", serializeTypeExpr(of))
    case s: Set                               => CollectionDto("Set", serializeTypeExpr(s.of))
    case Graph(_, of)                         => CollectionDto("Graph", serializeTypeExpr(of))
    case Replica(_, of)                       => CollectionDto("Replica", serializeTypeExpr(of))
    case Mapping(_, from, to)                 => MappingDto(serializeTypeExpr(from), serializeTypeExpr(to))
    case Table(_, of, dims)                   => TableDto(serializeTypeExpr(of), dims)
    case EntityReferenceTypeExpression(_, e)  => EntityRefDto(Some(path(e)))
    case Optional(_, inner)                   => CardinalityDto("optional", serializeTypeExpr(inner))
    case ZeroOrMore(_, inner)                 => CardinalityDto("zeroOrMore", serializeTypeExpr(inner))
    case OneOrMore(_, inner)                  => CardinalityDto("oneOrMore", serializeTypeExpr(inner))
    case SpecificRange(_, inner, mn, mx) =>
      CardinalityDto("range", serializeTypeExpr(inner), Some(mn), Some(mx))
    case a: AggregateTypeExpression =>
      RecordDto(a.fields.map(serializeField), a.methods.map(serializeMethod))
    case p: PredefinedType => PredefDto(p.getClass.getSimpleName.replace("$", ""))

  private def serializeField(f: Field): FieldDto =
    FieldDto(f.id.value, serializeTypeExpr(f.typeEx), briefOf(f.metadata))

  private def serializeMethod(m: Method): MethodDto =
    val args = m.args.map(a => MethodArgDto(a.name, serializeTypeExpr(a.typeEx)))
    MethodDto(m.id.value, serializeTypeExpr(m.typeEx), args, briefOf(m.metadata))

  private def serializeType(t: Type): TypeDefDto =
    TypeDefDto(t.id.value, serializeTypeExpr(t.typEx), briefOf(t.metadata), metaOf(t.metadata))

  // ---------------------------------------------------------------------------
  // Statements
  // ---------------------------------------------------------------------------

  private def serializeStatements(c: Contents[Statements]): Seq[StatementDto] =
    c.toSeq.collect { case s: Statement => serializeStatement(s) }

  private def serializeStatement(s: Statement): StatementDto = s match
    case PromptStatement(_, what)  => PromptStmtDto(what.s)
    case ErrorStatement(_, msg)    => ErrorStmtDto(msg.s)
    case LetStatement(_, id, tr, e) =>
      LetStmtDto(id.value, tr.map(t => path(t.pathId)), e.s)
    case CodeStatement(_, lang, body) => CodeStmtDto(lang.s, body)
    case RequireStatement(_, cond) =>
      cond match
        case ls: LiteralString => RequireStmtDto(Some(ls.s), None)
        case ir: InvariantRef  => RequireStmtDto(None, Some(path(ir.pathId)))
    case SetStatement(_, field, value) =>
      field match
        case fr: FieldRef => SetStmtDto(Some(path(fr.pathId)), None, value.s)
        case sr: StateRef => SetStmtDto(None, Some(path(sr.pathId)), value.s)
    case SendStatement(_, msg, portlet) =>
      val (pp, pk) = portletRef(portlet)
      SendStmtDto(messageRefDto(msg), pp, pk)
    case MorphStatement(_, entity, state, value) =>
      MorphStmtDto(path(entity.pathId), path(state.pathId), messageRefDto(value))
    case BecomeStatement(_, entity, handler) =>
      BecomeStmtDto(path(entity.pathId), path(handler.pathId))
    case TellStatement(_, msg, proc) =>
      val (pp, pk) = processorRef(proc)
      TellStmtDto(messageRefDto(msg), pp, pk)
    case ReplyStatement(_, msg) => ReplyStmtDto(messageRefDto(msg))
    case WhenStatement(_, cond, thenS, elseS, negated) =>
      cond match
        case ls: LiteralString =>
          WhenStmtDto(Some(ls.s), None, negated, serializeStatements(thenS), serializeStatements(elseS))
        case id: Identifier =>
          WhenStmtDto(None, Some(id.value), negated, serializeStatements(thenS), serializeStatements(elseS))
    case MatchStatement(_, expr, cases, default) =>
      val cs = cases.map(c => MatchCaseDto(c.pattern.s, serializeStatements(c.statements)))
      MatchStmtDto(expr.s, cs, serializeStatements(default))

  // ---------------------------------------------------------------------------
  // Interactions
  // ---------------------------------------------------------------------------

  private def serializeInteractions(c: Contents[InteractionContainerContents]): Seq[InteractionDto] =
    c.toSeq.collect { case i: Interaction => serializeInteraction(i) }

  private def serializeInteraction(i: Interaction): InteractionDto = i match
    case VagueInteraction(_, from, rel, to, _) => VagueIxnDto(from.s, rel.s, to.s)
    case SendMessageInteraction(_, from, msg, to, _) =>
      val (pp, pk) = processorRef(to)
      SendMessageIxnDto(refDto(from), messageRefDto(msg), pp, pk)
    case ArbitraryInteraction(_, from, rel, to, _) => ArbitraryIxnDto(refDto(from), rel.s, refDto(to))
    case SelfInteraction(_, from, rel, _)          => SelfIxnDto(refDto(from), rel.s)
    case FocusOnGroupInteraction(_, user, group, _) =>
      FocusOnGroupIxnDto(path(user.pathId), path(group.pathId))
    case DirectUserToURLInteraction(_, user, url, _) =>
      DirectToURLIxnDto(path(user.pathId), url.toExternalForm)
    case ShowOutputInteraction(_, output, rel, user, _) =>
      ShowOutputIxnDto(path(output.pathId), rel.s, path(user.pathId))
    case SelectInputInteraction(_, user, input, _) => SelectInputIxnDto(path(user.pathId), path(input.pathId))
    case TakeInputInteraction(_, user, input, _)   => TakeInputIxnDto(path(user.pathId), path(input.pathId))
    case ParallelInteractions(_, contents, _)      => ParallelIxnDto(serializeInteractions(contents))
    case SequentialInteractions(_, contents, _)    => SequentialIxnDto(serializeInteractions(contents))
    case OptionalInteractions(_, contents, _)      => OptionalIxnDto(serializeInteractions(contents))

  // ---------------------------------------------------------------------------
  // Leaf / supporting definitions
  // ---------------------------------------------------------------------------

  private def serializeAuthor(a: Author): AuthorDto =
    AuthorDto(a.id.value, a.name.s, a.email.s, a.organization.map(_.s), a.title.map(_.s))

  private def serializeUser(u: User): UserDto =
    UserDto(u.id.value, u.is_a.s, briefOf(u.metadata))

  private def serializeConstant(c: Constant): ConstantDto =
    ConstantDto(c.id.value, serializeTypeExpr(c.typeEx), c.value.s, briefOf(c.metadata))

  private def serializeInvariant(i: Invariant): InvariantDto =
    InvariantDto(i.id.value, i.condition.map(_.s).getOrElse(""), briefOf(i.metadata))

  private def serializeHandler(h: Handler): HandlerDto =
    val clauses = h.contents.toSeq.collect { case oc: OnClause => serializeOnClause(oc) }
    HandlerDto(h.id.value, briefOf(h.metadata), clauses)

  private def serializeOnClause(oc: OnClause): OnClauseDto = oc match
    case omc: OnMessageClause =>
      OnClauseDto("message", Some(messageRefDto(omc.msg)), serializeStatements(omc.contents))
    case _: OnInitializationClause => OnClauseDto("init", None, serializeStatements(oc.contents))
    case _: OnTerminationClause    => OnClauseDto("term", None, serializeStatements(oc.contents))
    case _                         => OnClauseDto("other", None, serializeStatements(oc.contents))

  private def aggFields(agg: Option[Aggregation]): Seq[FieldDto] =
    agg.toSeq.flatMap(_.fields.map(serializeField))

  private def serializeFunction(f: Function): FunctionDto =
    val types = f.contents.toSeq.collect { case t: Type => serializeType(t) }
    val statements = f.contents.toSeq.collect { case s: Statement => serializeStatement(s) }
    val functions = f.contents.toSeq.collect { case nf: Function => serializeFunction(nf) }
    FunctionDto(f.id.value, briefOf(f.metadata), aggFields(f.input), aggFields(f.output), types, statements, functions)

  private def serializeSagaStep(st: SagaStep): SagaStepDto =
    SagaStepDto(st.id.value, serializeStatements(st.doStatements), serializeStatements(st.undoStatements),
      briefOf(st.metadata))

  private def serializeSaga(s: Saga): SagaDto =
    val types = s.contents.toSeq.collect { case t: Type => serializeType(t) }
    val steps = s.contents.toSeq.collect { case st: SagaStep => serializeSagaStep(st) }
    SagaDto(s.id.value, briefOf(s.metadata), aggFields(s.input), aggFields(s.output), types, steps)

  // ---------------------------------------------------------------------------
  // Streaming & integration
  // ---------------------------------------------------------------------------

  private def serializeAdaptor(a: Adaptor): AdaptorDto =
    val dir = a.direction match
      case _: InboundAdaptor  => "inbound"
      case _: OutboundAdaptor => "outbound"
    AdaptorDto(
      a.id.value,
      dir,
      path(a.referent.pathId),
      briefOf(a.metadata),
      a.contents.toSeq.collect { case t: Type => serializeType(t) },
      a.contents.toSeq.collect { case c: Constant => serializeConstant(c) },
      a.contents.toSeq.collect { case f: Function => serializeFunction(f) },
      a.contents.toSeq.collect { case h: Handler => serializeHandler(h) }
    )

  private def serializePortlet(id: String, tr: TypeRef, md: Contents[MetaData]): PortletDto =
    PortletDto(id, path(tr.pathId), briefOf(md))

  private def serializeConnector(c: Connector): ConnectorDto =
    ConnectorDto(c.id.value, path(c.from.pathId), path(c.to.pathId), briefOf(c.metadata))

  private def serializeStreamlet(s: Streamlet): StreamletDto =
    val shape = s.shape match
      case _: Source => "source"
      case _: Sink   => "sink"
      case _: Flow   => "flow"
      case _: Merge  => "merge"
      case _: Split  => "split"
      case _: Router => "router"
      case _         => "void"
    StreamletDto(
      s.id.value,
      shape,
      briefOf(s.metadata),
      s.contents.toSeq.collect { case i: Inlet => serializePortlet(i.id.value, i.type_, i.metadata) },
      s.contents.toSeq.collect { case o: Outlet => serializePortlet(o.id.value, o.type_, o.metadata) },
      s.contents.toSeq.collect { case cn: Connector => serializeConnector(cn) },
      s.contents.toSeq.collect { case t: Type => serializeType(t) },
      s.contents.toSeq.collect { case h: Handler => serializeHandler(h) }
    )

  private def serializeRelationship(r: Relationship): RelationshipDto =
    val (p, kind) = processorRef(r.withProcessor)
    RelationshipDto(r.id.value, p, kind, r.cardinality.proportion, r.label.map(_.s), briefOf(r.metadata))

  private def serializeProjector(p: Projector): ProjectorDto =
    ProjectorDto(
      p.id.value,
      briefOf(p.metadata),
      p.contents.toSeq.collectFirst { case rr: RepositoryRef => path(rr.pathId) },
      p.contents.toSeq.collect { case t: Type => serializeType(t) },
      p.contents.toSeq.collect { case c: Constant => serializeConstant(c) },
      p.contents.toSeq.collect { case f: Function => serializeFunction(f) },
      p.contents.toSeq.collect { case h: Handler => serializeHandler(h) }
    )

  private def serializeSchema(sc: Schema): SchemaDto =
    SchemaDto(
      sc.id.value,
      Some(sc.schemaKind.toString),
      sc.data.map { case (id, tr) => id.value -> path(tr.pathId) },
      sc.links.map { case (id, (a, b)) => id.value -> Seq(path(a.pathId), path(b.pathId)) },
      sc.indices.map(fr => path(fr.pathId)),
      briefOf(sc.metadata)
    )

  private def serializeRepository(r: Repository): RepositoryDto =
    RepositoryDto(
      r.id.value,
      briefOf(r.metadata),
      r.contents.toSeq.collectFirst { case sc: Schema => serializeSchema(sc) },
      r.contents.toSeq.collect { case t: Type => serializeType(t) },
      r.contents.toSeq.collect { case h: Handler => serializeHandler(h) }
    )

  // ---------------------------------------------------------------------------
  // UI groups
  // ---------------------------------------------------------------------------

  private def serializePutOut(p: TypeRef | ConstantRef | LiteralString): PutOutDto = p match
    case tr: TypeRef       => PutOutDto("type", path(tr.pathId), Some(tr.keyword))
    case cr: ConstantRef   => PutOutDto("constant", path(cr.pathId), None)
    case ls: LiteralString => PutOutDto("literal", ls.s, None)

  private def serializeInput(in: Input): InputDto =
    InputDto(
      in.id.value,
      path(in.takeIn.pathId),
      Some(in.nounAlias),
      Some(in.verbAlias),
      briefOf(in.metadata),
      in.contents.toSeq.collect { case i: Input => serializeInput(i) }
    )

  private def serializeOutput(o: Output): OutputDto =
    OutputDto(
      o.id.value,
      serializePutOut(o.putOut),
      Some(o.nounAlias),
      Some(o.verbAlias),
      briefOf(o.metadata),
      o.contents.toSeq.collect { case out: Output => serializeOutput(out) }
    )

  private def serializeContainedGroup(cg: ContainedGroup): ContainedGroupDto =
    ContainedGroupDto(cg.id.value, path(cg.group.pathId), briefOf(cg.metadata))

  private def serializeGroup(g: Group): GroupDto =
    GroupDto(
      g.id.value,
      Some(g.alias),
      briefOf(g.metadata),
      g.contents.toSeq.collect { case sub: Group => serializeGroup(sub) },
      g.contents.toSeq.collect { case cg: ContainedGroup => serializeContainedGroup(cg) },
      g.contents.toSeq.collect { case i: Input => serializeInput(i) },
      g.contents.toSeq.collect { case o: Output => serializeOutput(o) }
    )

  // ---------------------------------------------------------------------------
  // Epics, use cases
  // ---------------------------------------------------------------------------

  private def serializeUserStory(us: UserStory): UserStoryDto =
    UserStoryDto(path(us.user.pathId), us.capability.s, us.benefit.s)

  private def serializeUseCase(uc: UseCase): UseCaseDto =
    val interactions = uc.contents.toSeq.collect { case i: Interaction => serializeInteraction(i) }
    UseCaseDto(uc.id.value, serializeUserStory(uc.userStory), interactions, briefOf(uc.metadata))

  private def serializeEpic(e: Epic): EpicDto =
    val shownBy = e.contents.toSeq.collect { case s: ShownBy => s.urls.map(_.toExternalForm) }.flatten
    val types = e.contents.toSeq.collect { case t: Type => serializeType(t) }
    val useCases = e.contents.toSeq.collect { case uc: UseCase => serializeUseCase(uc) }
    EpicDto(e.id.value, serializeUserStory(e.userStory), briefOf(e.metadata), shownBy, types, useCases)

  // ---------------------------------------------------------------------------
  // Containers
  // ---------------------------------------------------------------------------

  private def usecaseOf(t: Type): Option[AggregateUseCase] = t.typEx match
    case a: AggregateUseCaseTypeExpression => Some(a.usecase)
    case _                                 => None

  private def messageFields(t: Type): Seq[FieldDto] = t.typEx match
    case a: AggregateTypeExpression => a.fields.map(serializeField)
    case _                          => Nil

  private def messageDto(t: Type): MessageDto =
    MessageDto(t.id.value, briefOf(t.metadata), messageFields(t))

  // Seq, not Set: `import AST.*` shadows scala.Set with AST.Set (a type expr).
  private val messageCases: Seq[AggregateUseCase] =
    Seq(AggregateUseCase.CommandCase, AggregateUseCase.EventCase, AggregateUseCase.QueryCase,
      AggregateUseCase.ResultCase)

  private def serializeEntity(e: Entity): EntityDto =
    val state = e.contents.toSeq.collectFirst { case s: State => StateDto(s.id.value, path(s.typ.pathId)) }
    EntityDto(
      e.id.value,
      briefOf(e.metadata),
      state,
      e.contents.toSeq.collect { case t: Type => serializeType(t) },
      e.contents.toSeq.collect { case c: Constant => serializeConstant(c) },
      e.contents.toSeq.collect { case f: Function => serializeFunction(f) },
      e.contents.toSeq.collect { case h: Handler => serializeHandler(h) },
      e.contents.toSeq.collect { case i: Invariant => serializeInvariant(i) },
      metaOf(e.metadata)
    )

  private def serializeContext(c: Context): ContextDto =
    val types0 = c.contents.toSeq.collect { case t: Type => t }
    def msgs(uc: AggregateUseCase) = types0.filter(t => usecaseOf(t).contains(uc)).map(messageDto)
    val plainTypes =
      types0.filterNot(t => usecaseOf(t).exists(messageCases.contains)).map(serializeType)
    ContextDto(
      c.id.value,
      briefOf(c.metadata),
      plainTypes,
      c.contents.toSeq.collect { case ct: Constant => serializeConstant(ct) },
      msgs(AggregateUseCase.CommandCase),
      msgs(AggregateUseCase.EventCase),
      msgs(AggregateUseCase.QueryCase),
      msgs(AggregateUseCase.ResultCase),
      c.contents.toSeq.collect { case e: Entity => serializeEntity(e) },
      c.contents.toSeq.collect { case f: Function => serializeFunction(f) },
      c.contents.toSeq.collect { case a: Adaptor => serializeAdaptor(a) },
      c.contents.toSeq.collect { case s: Streamlet => serializeStreamlet(s) },
      c.contents.toSeq.collect { case p: Projector => serializeProjector(p) },
      c.contents.toSeq.collect { case r: Repository => serializeRepository(r) },
      c.contents.toSeq.collect { case cn: Connector => serializeConnector(cn) },
      c.contents.toSeq.collect { case r: Relationship => serializeRelationship(r) },
      c.contents.toSeq.collect { case s: Saga => serializeSaga(s) },
      c.contents.toSeq.collect { case g: Group => serializeGroup(g) },
      c.contents.toSeq.collect { case h: Handler => serializeHandler(h) },
      metaOf(c.metadata)
    )

  private def serializeDomain(d: Domain): DomainDto =
    DomainDto(
      d.id.value,
      briefOf(d.metadata),
      d.contents.toSeq.collect { case a: Author => serializeAuthor(a) },
      d.contents.toSeq.collect { case u: User => serializeUser(u) },
      d.contents.toSeq.collect { case t: Type => serializeType(t) },
      d.contents.toSeq.collect { case s: Saga => serializeSaga(s) },
      d.contents.toSeq.collect { case e: Epic => serializeEpic(e) },
      d.contents.toSeq.collect { case sub: Domain => serializeDomain(sub) },
      d.contents.toSeq.collect { case c: Context => serializeContext(c) },
      metaOf(d.metadata)
    )

  private def serializeModule(m: Module): ModuleDto =
    ModuleDto(
      m.id.value,
      briefOf(m.metadata),
      m.contents.toSeq.collect { case a: Author => serializeAuthor(a) },
      m.contents.toSeq.collect { case d: Domain => serializeDomain(d) }
    )

end JsonSerializer
