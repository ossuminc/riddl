/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.{empty, Messages}
import com.ossuminc.riddl.language.{Contents, toSeq}
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInput, PassOutput, PassRoot, PassesOutput}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

/** Serializes an AST `Root` to the [[JsonModel]] wire schema using the trusted
  * `HierarchyPass` infrastructure — the inverse of [[JsonAstBuilder]].
  *
  * Because a `HierarchyPass` fires `openContainer`/`closeContainer` for **every**
  * container node (each entity state, each handler, each type, …), the walk is
  * complete by construction: it cannot silently drop the 2nd/3rd state or a
  * state-nested handler the way a hand-recursive walk can. Each container builds
  * its DTO from the child DTOs its own `closeContainer` pushed onto the scope
  * stack; leaf/expression internals (type expressions, statements, refs) are
  * read directly from the node via pure helpers.
  */
object JsonifierPass:
  val name: String = "jsonify"

  def creator(using PlatformContext): PassCreator =
    (in: PassInput, out: PassesOutput) => new JsonifierPass(in, out)

case class JsonifierOutput(
  root: PassRoot,
  messages: Messages,
  rootDto: JsonModel.RootDto
)(using PlatformContext)
    extends PassOutput

class JsonifierPass(input: PassInput, outputs: PassesOutput)(using PlatformContext)
    extends com.ossuminc.riddl.passes.HierarchyPass(input, outputs):

  import JsonModel.*

  def name: String = JsonifierPass.name

  // Tags to disambiguate children that share a DTO type or need a use case.
  private case class MsgChild(useCase: AggregateUseCase, dto: MessageDto)
  private case class InletChild(dto: PortletDto)
  private case class OutletChild(dto: PortletDto)

  private val stack = mutable.Stack.empty[mutable.ArrayBuffer[Any]]
  private var rootDto: RootDto = RootDto()

  private def add(x: Any): Unit = if stack.nonEmpty then stack.top += x

  override protected def openContainer(definition: Definition, parents: Parents): Unit =
    stack.push(mutable.ArrayBuffer.empty[Any])

  override protected def processLeaf(definition: Leaf, parents: Parents): Unit =
    buildLeaf(definition).foreach(add)

  override protected def processValue(value: RiddlValue, parents: Parents): Unit = ()

  override protected def closeContainer(definition: Definition, parents: Parents): Unit =
    val kids = stack.pop().toSeq
    buildContainer(definition, kids) match
      case Some(r: RootDto) if parents.isEmpty => rootDto = r
      case Some(built)                         => add(built)
      case None                                => ()

  def result(root: PassRoot): PassOutput = JsonifierOutput(root, empty, rootDto)

  // ---------------------------------------------------------------------------
  // Container assembly (children come from the scope; node internals read direct)
  // ---------------------------------------------------------------------------

  private def buildContainer(d: Definition, kids: Seq[Any]): Option[Any] =
    def col[T: reflect.ClassTag]: Seq[T] = kids.collect { case t: T => t }
    def msgs(uc: AggregateUseCase): Seq[MessageDto] = kids.collect { case MsgChild(u, m) if u == uc => m }
    d match
      case r: Root =>
        Some(RootDto(col[DomainDto], col[ModuleDto]))
      case m: Module =>
        Some(ModuleDto(m.id.value, briefOf(m.metadata), col[AuthorDto], col[DomainDto]))
      case dom: Domain =>
        Some(
          DomainDto(dom.id.value, briefOf(dom.metadata), col[AuthorDto], col[UserDto], col[TypeDefDto],
            col[SagaDto], col[EpicDto], col[DomainDto], col[ContextDto], metaOf(dom.metadata))
        )
      case c: Context =>
        Some(
          ContextDto(c.id.value, briefOf(c.metadata), col[TypeDefDto], col[ConstantDto],
            msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
            msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[EntityDto],
            col[FunctionDto], col[AdaptorDto], col[StreamletDto], col[ProjectorDto], col[RepositoryDto],
            col[ConnectorDto], col[RelationshipDto], col[SagaDto], col[GroupDto], col[HandlerDto],
            metaOf(c.metadata))
        )
      case e: Entity =>
        Some(
          EntityDto(e.id.value, briefOf(e.metadata), None, col[StateDto], col[TypeDefDto], col[ConstantDto],
            msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
            msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[FunctionDto],
            col[HandlerDto], col[InvariantDto], metaOf(e.metadata))
        )
      case s: State =>
        Some(StateDto(s.id.value, path(s.typ.pathId), col[HandlerDto], briefOf(s.metadata)))
      case h: Handler =>
        Some(HandlerDto(h.id.value, briefOf(h.metadata), col[OnClauseDto]))
      case oc: OnClause =>
        val statements = serializeStatements(oc.contents)
        oc match
          case omc: OnMessageClause      => Some(OnClauseDto("message", Some(messageRefDto(omc.msg)), statements))
          case _: OnInitializationClause => Some(OnClauseDto("init", None, statements))
          case _: OnTerminationClause    => Some(OnClauseDto("term", None, statements))
          case _                         => Some(OnClauseDto("other", None, statements))
      case t: Type =>
        t.typEx match
          case a: AggregateUseCaseTypeExpression if messageUseCase(a.usecase) =>
            Some(MsgChild(a.usecase, MessageDto(t.id.value, briefOf(t.metadata), a.fields.map(serializeField))))
          case _ =>
            Some(TypeDefDto(t.id.value, serializeTypeExpr(t.typEx), briefOf(t.metadata), metaOf(t.metadata)))
      case a: Adaptor =>
        val dir = a.direction match
          case _: InboundAdaptor => "inbound"
          case _                 => "outbound"
        Some(AdaptorDto(a.id.value, dir, path(a.referent.pathId), briefOf(a.metadata), col[TypeDefDto],
          col[ConstantDto], msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
          msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[FunctionDto],
          col[HandlerDto]))
      case s: Streamlet =>
        val shape = s.shape match
          case _: Source => "source"
          case _: Sink   => "sink"
          case _: Flow   => "flow"
          case _: Merge  => "merge"
          case _: Split  => "split"
          case _: Router => "router"
          case _         => "void"
        Some(StreamletDto(s.id.value, shape, briefOf(s.metadata), col[InletChild].map(_.dto),
          col[OutletChild].map(_.dto), col[ConnectorDto], col[TypeDefDto],
          msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
          msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[HandlerDto]))
      case p: Projector =>
        val repo = p.contents.toSeq.collectFirst { case rr: RepositoryRef => path(rr.pathId) }
        Some(ProjectorDto(p.id.value, briefOf(p.metadata), repo, col[TypeDefDto], col[ConstantDto],
          msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
          msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[FunctionDto],
          col[HandlerDto]))
      case r: Repository =>
        Some(RepositoryDto(r.id.value, briefOf(r.metadata), col[SchemaDto].headOption, col[TypeDefDto],
          msgs(AggregateUseCase.CommandCase), msgs(AggregateUseCase.EventCase),
          msgs(AggregateUseCase.QueryCase), msgs(AggregateUseCase.ResultCase), col[HandlerDto]))
      case s: Saga =>
        Some(SagaDto(s.id.value, briefOf(s.metadata), aggFields(s.input), aggFields(s.output), col[TypeDefDto],
          col[SagaStepDto]))
      case f: Function =>
        Some(FunctionDto(f.id.value, briefOf(f.metadata), aggFields(f.input), aggFields(f.output),
          col[TypeDefDto], f.contents.toSeq.collect { case st: Statement => serializeStatement(st) },
          col[FunctionDto]))
      case g: Group =>
        Some(GroupDto(g.id.value, Some(g.alias), briefOf(g.metadata), col[GroupDto], col[ContainedGroupDto],
          col[InputDto], col[OutputDto]))
      case in: Input =>
        Some(InputDto(in.id.value, path(in.takeIn.pathId), Some(in.nounAlias), Some(in.verbAlias),
          briefOf(in.metadata), col[InputDto]))
      case o: Output =>
        Some(OutputDto(o.id.value, serializePutOut(o.putOut), Some(o.nounAlias), Some(o.verbAlias),
          briefOf(o.metadata), col[OutputDto]))
      case uc: UseCase =>
        val interactions = uc.contents.toSeq.collect { case i: Interaction => serializeInteraction(i) }
        Some(UseCaseDto(uc.id.value, serializeUserStory(uc.userStory), interactions, briefOf(uc.metadata)))
      case e: Epic =>
        val shownBy = e.contents.toSeq.collect { case s: ShownBy => s.urls.map(_.toExternalForm) }.flatten
        Some(EpicDto(e.id.value, serializeUserStory(e.userStory), briefOf(e.metadata), shownBy, col[TypeDefDto],
          col[UseCaseDto]))
      case _ => None // interaction containers etc. — read from their parent node directly

  private def buildLeaf(l: Leaf): Option[Any] = l match
    case c: Constant     => Some(ConstantDto(c.id.value, serializeTypeExpr(c.typeEx), c.value.s, briefOf(c.metadata)))
    case i: Invariant    => Some(InvariantDto(i.id.value, i.condition.map(_.s).getOrElse(""), briefOf(i.metadata)))
    case u: User         => Some(UserDto(u.id.value, u.is_a.s, briefOf(u.metadata)))
    case a: Author       => Some(AuthorDto(a.id.value, a.name.s, a.email.s, a.organization.map(_.s), a.title.map(_.s)))
    case i: Inlet        => Some(InletChild(PortletDto(i.id.value, path(i.type_.pathId), briefOf(i.metadata))))
    case o: Outlet       => Some(OutletChild(PortletDto(o.id.value, path(o.type_.pathId), briefOf(o.metadata))))
    case c: Connector    => Some(ConnectorDto(c.id.value, path(c.from.pathId), path(c.to.pathId), briefOf(c.metadata)))
    case r: Relationship =>
      val (p, kind) = processorRef(r.withProcessor)
      Some(RelationshipDto(r.id.value, p, kind, r.cardinality.proportion, r.label.map(_.s), briefOf(r.metadata)))
    case sc: Schema =>
      Some(SchemaDto(sc.id.value, Some(sc.schemaKind.toString),
        sc.data.map { case (id, tr) => id.value -> path(tr.pathId) },
        sc.links.map { case (id, (a, b)) => id.value -> Seq(path(a.pathId), path(b.pathId)) },
        sc.indices.map(fr => path(fr.pathId)), briefOf(sc.metadata)))
    case st: SagaStep =>
      Some(SagaStepDto(st.id.value, serializeStatements(st.doStatements), serializeStatements(st.undoStatements),
        briefOf(st.metadata)))
    case cg: ContainedGroup => Some(ContainedGroupDto(cg.id.value, path(cg.group.pathId), briefOf(cg.metadata)))
    case _                  => None // Field / Method / Enumerator — captured via their Type's typEx

  // ---------------------------------------------------------------------------
  // Pure helpers (leaf/expression internals) — inverse of JsonAstBuilder
  // ---------------------------------------------------------------------------

  private def messageUseCase(uc: AggregateUseCase): Boolean =
    uc == AggregateUseCase.CommandCase || uc == AggregateUseCase.EventCase ||
      uc == AggregateUseCase.QueryCase || uc == AggregateUseCase.ResultCase

  private def path(pid: PathIdentifier): String = pid.value.mkString(".")

  private def briefOf(md: Contents[MetaData]): Option[String] =
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

  private def serializeField(f: Field): FieldDto =
    FieldDto(f.id.value, serializeTypeExpr(f.typeEx), briefOf(f.metadata))

  private def serializeMethod(m: Method): MethodDto =
    MethodDto(m.id.value, serializeTypeExpr(m.typeEx),
      m.args.map(a => MethodArgDto(a.name, serializeTypeExpr(a.typeEx))), briefOf(m.metadata))

  private def aggFields(agg: Option[Aggregation]): Seq[FieldDto] =
    agg.toSeq.flatMap(_.fields.map(serializeField))

  private def serializeTypeExpr(te: TypeExpression): TypeExprDto = te match
    case String_(_, min, max)                => StringDto(min, max)
    case UniqueId(_, p)                       => IdDto(Some(path(p)))
    case Currency(_, c)                       => CurrencyDto(Some(c))
    case Pattern(_, ps)                       => PatternDto(ps.map(_.s))
    case URI(_, scheme)                       => URIDto(scheme.map(_.s))
    case Blob(_, bk)                          => BlobDto(Some(bk.toString))
    case ZonedDate(_, z)                      => ZonedDto("ZonedDate", z.map(_.s))
    case ZonedDateTime(_, z)                  => ZonedDto("ZonedDateTime", z.map(_.s))
    case _: Bool                             => PredefDto("Boolean")
    case Decimal(_, w, f)                    => DecimalDto(Some(w), Some(f))
    case RangeType(_, mn, mx)                => RangeDto(Some(mn), Some(mx))
    case Enumeration(_, es)                   => EnumDto(es.toSeq.map(e => EnumeratorDto(e.id.value, e.enumVal)))
    case Alternation(_, of)                   => AlternationDto(of.toSeq.map(a => path(a.pathId)))
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

  private def serializeStatements(c: Contents[Statements]): Seq[StatementDto] =
    c.toSeq.collect { case s: Statement => serializeStatement(s) }

  private def serializeStatement(s: Statement): StatementDto = s match
    case PromptStatement(_, what)     => PromptStmtDto(what.s)
    case ErrorStatement(_, msg)       => ErrorStmtDto(msg.s)
    case LetStatement(_, id, tr, e)   => LetStmtDto(id.value, tr.map(t => path(t.pathId)), e.s)
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
      val (pp, pk) = portletRef(portlet); SendStmtDto(messageRefDto(msg), pp, pk)
    case MorphStatement(_, entity, state, value) =>
      MorphStmtDto(path(entity.pathId), path(state.pathId), messageRefDto(value))
    case BecomeStatement(_, entity, handler) => BecomeStmtDto(path(entity.pathId), path(handler.pathId))
    case TellStatement(_, msg, proc) =>
      val (pp, pk) = processorRef(proc); TellStmtDto(messageRefDto(msg), pp, pk)
    case ReplyStatement(_, msg) => ReplyStmtDto(messageRefDto(msg))
    case WhenStatement(_, cond, thenS, elseS, negated) =>
      cond match
        case ls: LiteralString =>
          WhenStmtDto(Some(ls.s), None, negated, serializeStatements(thenS), serializeStatements(elseS))
        case id: Identifier =>
          WhenStmtDto(None, Some(id.value), negated, serializeStatements(thenS), serializeStatements(elseS))
    case MatchStatement(_, expr, cases, default) =>
      MatchStmtDto(expr.s, cases.map(c => MatchCaseDto(c.pattern.s, serializeStatements(c.statements))),
        serializeStatements(default))

  private def serializeInteraction(i: Interaction): InteractionDto = i match
    case VagueInteraction(_, from, rel, to, _)       => VagueIxnDto(from.s, rel.s, to.s)
    case SendMessageInteraction(_, from, msg, to, _) =>
      val (pp, pk) = processorRef(to); SendMessageIxnDto(refDto(from), messageRefDto(msg), pp, pk)
    case ArbitraryInteraction(_, from, rel, to, _)   => ArbitraryIxnDto(refDto(from), rel.s, refDto(to))
    case SelfInteraction(_, from, rel, _)            => SelfIxnDto(refDto(from), rel.s)
    case FocusOnGroupInteraction(_, user, group, _)  => FocusOnGroupIxnDto(path(user.pathId), path(group.pathId))
    case DirectUserToURLInteraction(_, user, url, _) => DirectToURLIxnDto(path(user.pathId), url.toExternalForm)
    case ShowOutputInteraction(_, output, rel, user, _) =>
      ShowOutputIxnDto(path(output.pathId), rel.s, path(user.pathId))
    case SelectInputInteraction(_, user, input, _) => SelectInputIxnDto(path(user.pathId), path(input.pathId))
    case TakeInputInteraction(_, user, input, _)   => TakeInputIxnDto(path(user.pathId), path(input.pathId))
    case ParallelInteractions(_, contents, _)      => ParallelIxnDto(serIxns(contents))
    case SequentialInteractions(_, contents, _)    => SequentialIxnDto(serIxns(contents))
    case OptionalInteractions(_, contents, _)      => OptionalIxnDto(serIxns(contents))

  private def serIxns(c: Contents[InteractionContainerContents]): Seq[InteractionDto] =
    c.toSeq.collect { case i: Interaction => serializeInteraction(i) }

  private def serializeUserStory(us: UserStory): UserStoryDto =
    UserStoryDto(path(us.user.pathId), us.capability.s, us.benefit.s)

  private def serializePutOut(p: TypeRef | ConstantRef | LiteralString): PutOutDto = p match
    case tr: TypeRef       => PutOutDto("type", path(tr.pathId), Some(tr.keyword))
    case cr: ConstantRef   => PutOutDto("constant", path(cr.pathId), None)
    case ls: LiteralString => PutOutDto("literal", ls.s, None)

  private def messageRefDto(mr: MessageRef): MessageRefDto = mr match
    case CommandRef(_, p) => MessageRefDto(path(p), "command")
    case EventRef(_, p)   => MessageRefDto(path(p), "event")
    case QueryRef(_, p)   => MessageRefDto(path(p), "query")
    case ResultRef(_, p)  => MessageRefDto(path(p), "result")
    case RecordRef(_, p)  => MessageRefDto(path(p), "record")
    case other            => MessageRefDto(path(other.pathId), "command")

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
    case UserRef(_, p)      => RefDto("user", path(p))
    case EntityRef(_, p)    => RefDto("entity", path(p))
    case ContextRef(_, p)   => RefDto("context", path(p))
    case GroupRef(_, _, p)  => RefDto("group", path(p))
    case OutputRef(_, _, p) => RefDto("output", path(p))
    case InputRef(_, _, p)  => RefDto("input", path(p))
    case AdaptorRef(_, p)   => RefDto("adaptor", path(p))
    case ProjectorRef(_, p) => RefDto("projector", path(p))
    case other              => RefDto("user", path(other.pathId))

end JsonifierPass
