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

/** Serializes an AST `Root` to the [[JsonModel]] wire schema using the trusted `HierarchyPass`
  * infrastructure — the inverse of [[JsonAstBuilder]].
  *
  * Because a `HierarchyPass` fires `openContainer`/`closeContainer` for **every** container node
  * (each entity state, each handler, each type, …), the walk is complete by construction: it cannot
  * silently drop the 2nd/3rd state or a state-nested handler the way a hand-recursive walk can.
  * Each container builds its DTO from the child DTOs its own `closeContainer` pushed onto the scope
  * stack; leaf/expression internals (type expressions, statements, refs) are read directly from the
  * node via pure helpers.
  */
object JsonifierPass:
  val name: String = "jsonify"

  def creator(using PlatformContext): PassCreator =
    (in: PassInput, out: PassesOutput) => new JsonifierPass(in, out)

case class JsonifierOutput(
  root: PassRoot,
  messages: Messages,
  rootDto: JsonModel.RootDto,
  /** `ParentKind -> ChildKind` pairs the schema could not express. Empty when the walk was
    * lossless. See [[JsonifierPass.droppedKinds]].
    */
  droppedKinds: Seq[(String, String)] = Nil
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

  /** Child DTOs consumed by the container currently being built, by identity.
    *
    * A parent assembles itself out of `col[T]` picks from its children. Anything it does not pick
    * is simply forgotten — which is how a whole class of silent data loss got in: the AST unions
    * widened (an entity became port-bearing, a domain gained connectors, a root gained authors) and
    * the DTOs did not follow, so those children were built, discarded, and never missed. Recording
    * what was picked lets the pass report what was not, instead of losing it quietly.
    */
  private var consumed = java.util.IdentityHashMap[Any, Boolean]()

  /** Kinds dropped, as `ParentKind -> ChildKind` seen at least once. */
  private val dropped = mutable.LinkedHashSet.empty[(String, String)]

  /** What the serializer could not express, for callers who want to know. Empty means the walk was
    * lossless with respect to the children the AST actually produced.
    */
  def droppedKinds: Seq[(String, String)] = dropped.toSeq

  override protected def openContainer(definition: Definition, parents: Parents): Unit =
    stack.push(mutable.ArrayBuffer.empty[Any])

  override protected def processLeaf(definition: Leaf, parents: Parents): Unit =
    buildLeaf(definition).foreach(add)

  /** A `Comment` is a `RiddlValue`, not a `Leaf`, so it arrives here rather than through
    * `processLeaf` — which is why comments in a definition's contents were invisible to this pass
    * and to its drop guard for so long. Pushing them onto the scope makes them ordinary children,
    * so a container that forgets to collect them now gets reported like any other loss.
    */
  override protected def processValue(value: RiddlValue, parents: Parents): Unit = value match
    case c: Comment if !isMetadataOfParent(c, parents) =>
      c match
        case lc: LineComment   => add(CommentDto(lc.text))
        case ic: InlineComment => add(CommentDto(ic.lines.mkString("\n"), inline = true))
    case _ => ()

  /** Whether this comment hangs off the parent's METADATA rather than sitting in its contents.
    * Metadata comments are already carried by `metaOf`, so counting them here as well would write
    * each one twice and move it into contents on the way back.
    */
  private def isMetadataOfParent(c: Comment, parents: Parents): Boolean =
    parents.headOption.exists(_.metadata.toSeq.exists(_ eq c))

  override protected def closeContainer(definition: Definition, parents: Parents): Unit =
    val kids = stack.pop().toSeq
    val outer = consumed
    consumed = java.util.IdentityHashMap[Any, Boolean]()
    val built = buildContainer(definition, kids).map(withContents(_, kids.flatMap(asContent)))
    for kid <- kids if !consumed.containsKey(kid) do
      dropped += (definition.getClass.getSimpleName -> kindOf(kid))
    end for
    consumed = outer
    built match
      case Some(r: RootDto) if parents.isEmpty => rootDto = r
      case Some(b)                             => add(b)
      case None                                => ()
  end closeContainer

  /** A child, as an entry of the ordered `contents` array.
    *
    * `Pass.traverse` walks `contents.foreach`, so `kids` is ALREADY in source order — the per-kind
    * buckets are precisely where that order was being thrown away. The three tagging wrappers are
    * unwrapped here, their discriminator moving onto the DTO where the `kind` tag can carry it.
    */
  private def asContent(kid: Any): Option[ContentDto] = kid match
    case MsgChild(uc, dto) => Some(dto.copy(usecase = Some(uc.useCase.toLowerCase())))
    case InletChild(dto)   => Some(dto.copy(direction = Some(ContentKind.Inlet)))
    case OutletChild(dto)  => Some(dto.copy(direction = Some(ContentKind.Outlet)))
    case c: ContentDto     => Some(c)
    case _                 => None

  /** Attach the ordered children to whichever container DTO was just built.
    *
    * Done in one place rather than threaded through the ~180 `k.col` picks at the construction
    * sites, which stay exactly as they are while both forms are written.
    */
  private def withContents(dto: Any, ordered: Seq[ContentDto]): Any = dto match
    case d: RootDto =>
      d.copy(
        contents = ordered,
        domains = Nil,
        modules = Nil,
        version = None,
        copyright = None,
        authors = Nil,
        comments = Nil
      )
    case d: ModuleDto =>
      d.copy(
        contents = ordered,
        authors = Nil,
        domains = Nil,
        types = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        constants = Nil,
        invariants = Nil,
        users = Nil,
        contexts = Nil,
        entities = Nil,
        adaptors = Nil,
        functions = Nil,
        projectors = Nil,
        repositories = Nil,
        streamlets = Nil,
        sagas = Nil,
        epics = Nil,
        connectors = Nil,
        relationships = Nil,
        modules = Nil,
        version = None,
        copyright = None,
        comments = Nil
      )
    case d: DomainDto =>
      d.copy(
        contents = ordered,
        authors = Nil,
        users = Nil,
        types = Nil,
        sagas = Nil,
        epics = Nil,
        domains = Nil,
        contexts = Nil,
        version = None,
        copyright = None,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        repositories = Nil,
        connectors = Nil,
        comments = Nil
      )
    case d: ContextDto =>
      d.copy(
        contents = ordered,
        types = Nil,
        constants = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        entities = Nil,
        functions = Nil,
        adaptors = Nil,
        streamlets = Nil,
        projectors = Nil,
        repositories = Nil,
        connectors = Nil,
        relationships = Nil,
        sagas = Nil,
        groups = Nil,
        handlers = Nil,
        inlets = Nil,
        outlets = Nil,
        version = None,
        copyright = None,
        invariants = Nil,
        comments = Nil
      )
    case d: EntityDto =>
      d.copy(
        contents = ordered,
        state = None,
        states = Nil,
        types = Nil,
        constants = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        functions = Nil,
        handlers = Nil,
        invariants = Nil,
        inlets = Nil,
        outlets = Nil,
        version = None,
        copyright = None,
        streamlets = Nil,
        connectors = Nil,
        relationships = Nil,
        comments = Nil
      )
    case d: StateDto =>
      d.copy(contents = ordered, handlers = Nil, invariants = Nil, comments = Nil)
    case d: HandlerDto =>
      d.copy(contents = ordered, onClauses = Nil, comments = Nil)
    case d: FunctionDto =>
      d.copy(contents = ordered, types = Nil, functions = Nil, comments = Nil)
    case d: AdaptorDto =>
      d.copy(
        contents = ordered,
        types = Nil,
        constants = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        functions = Nil,
        handlers = Nil,
        inlets = Nil,
        outlets = Nil,
        version = None,
        copyright = None,
        invariants = Nil,
        streamlets = Nil,
        connectors = Nil,
        relationships = Nil,
        comments = Nil
      )
    case d: StreamletDto =>
      d.copy(
        contents = ordered,
        inlets = Nil,
        outlets = Nil,
        connectors = Nil,
        types = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        handlers = Nil,
        version = None,
        copyright = None,
        constants = Nil,
        functions = Nil,
        invariants = Nil,
        streamlets = Nil,
        relationships = Nil,
        comments = Nil
      )
    case d: ProjectorDto =>
      d.copy(
        contents = ordered,
        types = Nil,
        constants = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        functions = Nil,
        handlers = Nil,
        inlets = Nil,
        outlets = Nil,
        version = None,
        copyright = None,
        invariants = Nil,
        streamlets = Nil,
        connectors = Nil,
        relationships = Nil,
        comments = Nil
      )
    case d: RepositoryDto =>
      d.copy(
        contents = ordered,
        schema = None,
        types = Nil,
        commands = Nil,
        events = Nil,
        queries = Nil,
        results = Nil,
        handlers = Nil,
        inlets = Nil,
        outlets = Nil,
        version = None,
        copyright = None,
        schemas = Nil,
        constants = Nil,
        functions = Nil,
        invariants = Nil,
        streamlets = Nil,
        connectors = Nil,
        relationships = Nil,
        comments = Nil
      )
    case d: SagaDto    => d.copy(contents = ordered, types = Nil, steps = Nil, comments = Nil)
    case d: EpicDto    => d.copy(contents = ordered, types = Nil, useCases = Nil, comments = Nil)
    case d: UseCaseDto => d.copy(contents = ordered, comments = Nil)
    case d: GroupDto =>
      d.copy(
        contents = ordered,
        groups = Nil,
        containedGroups = Nil,
        inputs = Nil,
        outputs = Nil,
        comments = Nil
      )
    // A Type (and so a MessageDto) and an OnClause deliberately have NO `contents`: a type's
    // children are derived from its type expression and already ordered inside `fields`, and an
    // on-clause body is ordered by `serializeStatements`, which keeps interleaved comments in
    // place. Giving either one a `contents` array would carry the same children twice.
    case other => other

  /** The reportable name of a child DTO, unwrapping the tagging wrappers. */
  private def kindOf(kid: Any): String = kid match
    case MsgChild(uc, _) => s"MessageDto($uc)"
    case _: InletChild   => "InletDto"
    case _: OutletChild  => "OutletDto"
    case other           => other.getClass.getSimpleName

  def result(root: PassRoot): PassOutput = JsonifierOutput(root, empty, rootDto, droppedKinds)

  // ---------------------------------------------------------------------------
  // Container assembly (children come from the scope; node internals read direct)
  // ---------------------------------------------------------------------------

  /** The children a container was handed, and the record of which it consumed.
    *
    * A parent assembles itself out of `col`/`msgs` picks. Anything it does not pick is forgotten,
    * so every pick is recorded and `closeContainer` reports the remainder.
    */
  private final class Kids(kids: Seq[Any]):
    private def keep[T](t: T): T = { consumed.put(t, true); t }
    def col[T: reflect.ClassTag]: Seq[T] = kids.collect { case t: T => keep(t) }
    def msgs(uc: AggregateUseCase): Seq[MessageDto] = kids.collect {
      case m @ MsgChild(u, dto) if u == uc => keep(m); dto
    }
  end Kids

  /** Build one container's DTO from the child DTOs its own `closeContainer` collected.
    *
    * Split across four builders by kind rather than written as one match: the whole thing exceeded
    * the coverage instrumenter's tree-node threshold, so it was silently skipped and could never be
    * measured. Each part is now small enough to instrument.
    */
  private def buildContainer(d: Definition, kids: Seq[Any]): Option[Any] =
    val k = Kids(kids)
    buildTopLevelDto(d, k)
      .orElse(buildProcessorDto(d, k))
      .orElse(buildBehaviorDto(d, k))
      .orElse(buildUiDto(d, k))
  end buildContainer

  /** A Root, a Module or a Domain: the containers that hold whole models. */
  private def buildTopLevelDto(d: Definition, k: Kids): Option[Any] =
    d match
      case r: Root =>
        Some(
          RootDto(
            k.col[DomainDto],
            k.col[ModuleDto],
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[AuthorDto],
            k.col[CommentDto]
          )
        )
      case m: Module =>
        // A Module is flat and may hold any top-level definition; every kind gets its own group.
        Some(
          ModuleDto(
            m.id.value,
            briefOf(m.metadata),
            k.col[AuthorDto],
            k.col[DomainDto],
            k.col[TypeDefDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[ConstantDto],
            k.col[InvariantDto],
            k.col[UserDto],
            k.col[ContextDto],
            k.col[EntityDto],
            k.col[AdaptorDto],
            k.col[FunctionDto],
            k.col[ProjectorDto],
            k.col[RepositoryDto],
            k.col[StreamletDto],
            k.col[SagaDto],
            k.col[EpicDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            k.col[ModuleDto],
            metaOf(m.metadata),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[CommentDto]
          )
        )
      case dom: Domain =>
        Some(
          DomainDto(
            dom.id.value,
            briefOf(dom.metadata),
            k.col[AuthorDto],
            k.col[UserDto],
            k.col[TypeDefDto],
            k.col[SagaDto],
            k.col[EpicDto],
            k.col[DomainDto],
            k.col[ContextDto],
            metaOf(dom.metadata),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[RepositoryDto],
            k.col[ConnectorDto],
            k.col[CommentDto]
          )
        )
      case _ => None
  end buildTopLevelDto

  /** The six Processors. They share `OccursInProcessor`, so their DTOs share most of their shape.
    */
  private def buildProcessorDto(d: Definition, k: Kids): Option[Any] =
    d match
      case c: Context =>
        Some(
          ContextDto(
            c.id.value,
            briefOf(c.metadata),
            k.col[TypeDefDto],
            k.col[ConstantDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[EntityDto],
            k.col[FunctionDto],
            k.col[AdaptorDto],
            k.col[StreamletDto],
            k.col[ProjectorDto],
            k.col[RepositoryDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            k.col[SagaDto],
            k.col[GroupDto],
            k.col[HandlerDto],
            metaOf(c.metadata),
            c.intention.map(_.keyword),
            c.ascribedShape.map(_.keyword),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[InvariantDto],
            k.col[CommentDto]
          )
        )
      case e: Entity =>
        Some(
          EntityDto(
            e.id.value,
            briefOf(e.metadata),
            None,
            k.col[StateDto],
            k.col[TypeDefDto],
            k.col[ConstantDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[FunctionDto],
            k.col[HandlerDto],
            k.col[InvariantDto],
            metaOf(e.metadata),
            e.ascribedShape.map(_.keyword),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[StreamletDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            k.col[CommentDto]
          )
        )
      case a: Adaptor =>
        val dir = a.direction match
          case _: InboundAdaptor => "inbound"
          case _                 => "outbound"
        Some(
          AdaptorDto(
            a.id.value,
            dir,
            path(a.referent.pathId),
            briefOf(a.metadata),
            k.col[TypeDefDto],
            k.col[ConstantDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[FunctionDto],
            k.col[HandlerDto],
            a.ascribedShape.map(_.keyword),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[InvariantDto],
            k.col[StreamletDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            metaOf(a.metadata),
            k.col[CommentDto]
          )
        )
      case s: Streamlet =>
        Some(
          StreamletDto(
            s.id.value,
            s.ascribedShape.map(_.keyword),
            briefOf(s.metadata),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[ConnectorDto],
            k.col[TypeDefDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[HandlerDto],
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[ConstantDto],
            k.col[FunctionDto],
            k.col[InvariantDto],
            k.col[StreamletDto],
            k.col[RelationshipDto],
            metaOf(s.metadata),
            k.col[CommentDto]
          )
        )
      case p: Projector =>
        val repo = p.contents.toSeq.collectFirst { case rr: RepositoryRef => path(rr.pathId) }
        Some(
          ProjectorDto(
            p.id.value,
            briefOf(p.metadata),
            repo,
            k.col[TypeDefDto],
            k.col[ConstantDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[FunctionDto],
            k.col[HandlerDto],
            p.ascribedShape.map(_.keyword),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[InvariantDto],
            k.col[StreamletDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            metaOf(p.metadata),
            k.col[CommentDto]
          )
        )
      case r: Repository =>
        Some(
          RepositoryDto(
            r.id.value,
            briefOf(r.metadata),
            // The plural `schemas` below carries them all; the singular stays empty on output so a
            // round trip cannot duplicate the first one. Reading still accepts either.
            None,
            k.col[TypeDefDto],
            k.msgs(AggregateUseCase.CommandCase),
            k.msgs(AggregateUseCase.EventCase),
            k.msgs(AggregateUseCase.QueryCase),
            k.msgs(AggregateUseCase.ResultCase),
            k.col[HandlerDto],
            r.ascribedShape.map(_.keyword),
            k.col[InletChild].map(_.dto),
            k.col[OutletChild].map(_.dto),
            k.col[VersionDto].headOption,
            k.col[CopyrightDto].headOption,
            k.col[SchemaDto],
            k.col[ConstantDto],
            k.col[FunctionDto],
            k.col[InvariantDto],
            k.col[StreamletDto],
            k.col[ConnectorDto],
            k.col[RelationshipDto],
            metaOf(r.metadata),
            k.col[CommentDto]
          )
        )
      case _ => None
  end buildProcessorDto

  /** The definitions that carry behaviour: states, handlers and their clauses, types, sagas,
    * functions.
    */
  private def buildBehaviorDto(d: Definition, k: Kids): Option[Any] =
    d match
      case s: State =>
        Some(
          StateDto(
            s.id.value,
            path(s.typ.pathId),
            k.col[HandlerDto],
            k.col[InvariantDto],
            briefOf(s.metadata),
            s.isInitial,
            metaOf(s.metadata),
            k.col[CommentDto]
          )
        )
      case h: Handler =>
        Some(
          HandlerDto(
            h.id.value,
            briefOf(h.metadata),
            k.col[OnClauseDto],
            h.isInitial,
            metaOf(h.metadata),
            k.col[CommentDto]
          )
        )
      case oc: OnClause =>
        // An on-clause's contents ARE its statement list, and `serializeStatements` carries the
        // comments in it. Consume the pushed comment children so the drop guard stays honest —
        // writing them again under `comments` would duplicate every one of them on rebuild.
        val statements = serializeStatements(oc.contents)
        k.col[CommentDto]
        oc match
          case omc: OnMessageClause =>
            // A55: carry the optional local message binding
            Some(
              OnClauseDto(
                "message",
                Some(messageRefDto(omc.msg)),
                statements,
                omc.binding.map(_.value),
                metaOf(omc.metadata),
                briefOf(omc.metadata)
              )
            )
          case oec: OnEventClause =>
            Some(
              OnClauseDto(
                "event",
                Some(messageRefDto(oec.msg)),
                statements,
                oec.binding.map(_.value),
                metaOf(oec.metadata),
                briefOf(oec.metadata)
              )
            )
          case _: OnInitializationClause =>
            Some(
              OnClauseDto(
                "init",
                None,
                statements,
                metadata = metaOf(oc.metadata),
                brief = briefOf(oc.metadata)
              )
            )
          case _: OnTerminationClause =>
            Some(
              OnClauseDto(
                "term",
                None,
                statements,
                metadata = metaOf(oc.metadata),
                brief = briefOf(oc.metadata)
              )
            )
          case _: OnActivationClause =>
            Some(
              OnClauseDto(
                "activate",
                None,
                statements,
                metadata = metaOf(oc.metadata),
                brief = briefOf(oc.metadata)
              )
            )
          case _: OnPassivationClause =>
            Some(
              OnClauseDto(
                "passivate",
                None,
                statements,
                metadata = metaOf(oc.metadata),
                brief = briefOf(oc.metadata)
              )
            )
          case _ =>
            Some(
              OnClauseDto(
                "other",
                None,
                statements,
                metadata = metaOf(oc.metadata),
                brief = briefOf(oc.metadata)
              )
            )
      case t: Type =>
        t.typEx match
          case a: AggregateUseCaseTypeExpression if messageUseCase(a.usecase) =>
            Some(
              MsgChild(
                a.usecase,
                MessageDto(
                  t.id.value,
                  briefOf(t.metadata),
                  a.fields.map(serializeField),
                  a.yields.map(messageRefDto),
                  metaOf(t.metadata),
                  commentsOf(a.contents)
                )
              )
            )
          case _ =>
            Some(
              TypeDefDto(
                t.id.value,
                serializeTypeExpr(t.typEx),
                briefOf(t.metadata),
                metaOf(t.metadata)
              )
            )
      case s: Saga =>
        Some(
          SagaDto(
            s.id.value,
            briefOf(s.metadata),
            argDto(s.input),
            argDto(s.output),
            k.col[TypeDefDto],
            k.col[SagaStepDto],
            metaOf(s.metadata),
            k.col[CommentDto]
          )
        )
      case f: Function =>
        Some(
          FunctionDto(
            f.id.value,
            briefOf(f.metadata),
            argDto(f.input),
            argDto(f.output),
            k.col[TypeDefDto],
            f.contents.toSeq.collect { case st: Statement => serializeStatement(st) },
            k.col[FunctionDto],
            metaOf(f.metadata),
            k.col[CommentDto]
          )
        )
      case _ => None
  end buildBehaviorDto

  /** The UI and epic surface: groups, their inputs and outputs, use cases and epics. */
  private def buildUiDto(d: Definition, k: Kids): Option[Any] =
    d match
      case g: Group =>
        Some(
          GroupDto(
            g.id.value,
            Some(g.alias),
            briefOf(g.metadata),
            k.col[GroupDto],
            k.col[ContainedGroupDto],
            k.col[InputDto],
            k.col[OutputDto],
            metaOf(g.metadata),
            k.col[CommentDto]
          )
        )
      case in: Input =>
        Some(
          InputDto(
            in.id.value,
            path(in.takeIn.pathId),
            Some(in.takeIn.keyword),
            Some(in.nounAlias),
            Some(in.verbAlias),
            briefOf(in.metadata),
            k.col[InputDto],
            metaOf(in.metadata)
          )
        )
      case o: Output =>
        Some(
          OutputDto(
            o.id.value,
            serializePutOut(o.putOut),
            Some(o.nounAlias),
            Some(o.verbAlias),
            briefOf(o.metadata),
            k.col[OutputDto],
            metaOf(o.metadata)
          )
        )
      case uc: UseCase =>
        val interactions = uc.contents.toSeq.collect { case i: Interaction =>
          serializeInteraction(i)
        }
        Some(
          UseCaseDto(
            uc.id.value,
            serializeUserStory(uc.userStory),
            interactions,
            briefOf(uc.metadata),
            metaOf(uc.metadata),
            k.col[CommentDto]
          )
        )
      case e: Epic =>
        val shownBy = e.contents.toSeq.collect { case s: ShownBy =>
          s.urls.map(_.toExternalForm)
        }.flatten
        Some(
          EpicDto(
            e.id.value,
            serializeUserStory(e.userStory),
            briefOf(e.metadata),
            shownBy,
            k.col[TypeDefDto],
            k.col[UseCaseDto],
            metaOf(e.metadata),
            k.col[CommentDto]
          )
        )
      case _ => None
  end buildUiDto

  private def buildLeaf(l: Leaf): Option[Any] = l match
    case c: Constant =>
      Some(
        ConstantDto(
          c.id.value,
          serializeTypeExpr(c.typeEx),
          c.value.s,
          briefOf(c.metadata),
          metaOf(c.metadata)
        )
      )
    case i: Invariant =>
      // A28: a LiteralString condition serializes to the `condition` string; a BooleanExpression to
      // the structured `expression` field (with `condition` empty).
      val (condStr, condExpr) = i.condition match
        case Some(ls: LiteralString)     => (ls.s, None)
        case Some(be: BooleanExpression) => ("", Some(serializeValue(be)))
        case None                        => ("", None)
      Some(InvariantDto(i.id.value, condStr, briefOf(i.metadata), condExpr, metaOf(i.metadata)))
    // A53: `name` is the rendered component; `numeric` records whether it was written as a number.
    case v: Version =>
      Some(VersionDto(v.component, v.isNumeric, briefOf(v.metadata), metaOf(v.metadata)))
    // A47: the notice is carried verbatim; the name identifies it for generators.
    case c: Copyright =>
      Some(CopyrightDto(c.id.value, c.notice, briefOf(c.metadata), metaOf(c.metadata)))
    case u: User => Some(UserDto(u.id.value, u.is_a.s, briefOf(u.metadata), metaOf(u.metadata)))
    case a: Author =>
      Some(
        AuthorDto(
          a.id.value,
          a.name.s,
          a.email.s,
          a.organization.map(_.s),
          a.title.map(_.s),
          metaOf(a.metadata)
        )
      )
    case i: Inlet =>
      Some(
        InletChild(
          PortletDto(
            i.id.value,
            path(i.type_.pathId),
            briefOf(i.metadata),
            metaOf(i.metadata),
            keyword = Some(i.type_.keyword)
          )
        )
      )
    case o: Outlet =>
      Some(
        OutletChild(
          PortletDto(
            o.id.value,
            path(o.type_.pathId),
            briefOf(o.metadata),
            metaOf(o.metadata),
            keyword = Some(o.type_.keyword)
          )
        )
      )
    case c: Connector =>
      Some(
        ConnectorDto(
          c.id.value,
          path(c.from.pathId),
          path(c.to.pathId),
          briefOf(c.metadata),
          metaOf(c.metadata)
        )
      )
    case r: Relationship =>
      val (p, kind) = processorRef(r.withProcessor)
      Some(
        RelationshipDto(
          r.id.value,
          p,
          kind,
          r.cardinality.proportion,
          r.label.map(_.s),
          briefOf(r.metadata),
          metaOf(r.metadata)
        )
      )
    case sc: Schema =>
      Some(
        SchemaDto(
          sc.id.value,
          Some(sc.schemaKind.toString),
          sc.data.map { case (id, tr) => id.value -> path(tr.pathId) },
          sc.links.map { case (id, (a, b)) => id.value -> Seq(path(a.pathId), path(b.pathId)) },
          sc.indices.map(fr => path(fr.pathId)),
          briefOf(sc.metadata),
          metaOf(sc.metadata)
        )
      )
    case st: SagaStep =>
      Some(
        SagaStepDto(
          st.id.value,
          serializeStatements(st.doStatements),
          serializeStatements(st.undoStatements),
          briefOf(st.metadata),
          metaOf(st.metadata)
        )
      )
    case cg: ContainedGroup =>
      Some(
        ContainedGroupDto(
          cg.id.value,
          path(cg.group.pathId),
          briefOf(cg.metadata),
          metaOf(cg.metadata)
        )
      )
    case _ => None // Field / Method / Enumerator — captured via their Type's typEx

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

  /** Comments sitting in a type expression's contents. Aggregates are serialized directly from the
    * node rather than through the child-DTO scope, so they need their own pick.
    */
  private def commentsOf[T <: RiddlValue](contents: Contents[T]): Seq[CommentDto] =
    contents.toSeq.collect {
      case lc: LineComment   => CommentDto(lc.text)
      case ic: InlineComment => CommentDto(ic.lines.mkString("\n"), inline = true)
    }

  private def metaOf(md: Contents[MetaData]): Option[MetaDto] =
    val items: Seq[RiddlValue] = md.toSeq
    val descr = items.collect { case d: BlockDescription => d.lines.map(_.s) }.flatten
    val terms = items.collect { case t: Term => TermDto(t.id.value, t.definition.map(_.s)) }
    val options = items.collect { case o: OptionValue => OptionDto(o.name, o.args.map(_.s)) }
    val authors = items.collect { case a: AuthorRef => path(a.pathId) }
    val attachments = items.collect {
      case fa: FileAttachment => AttachmentDto(fa.id.value, fa.mimeType, fa.inFile.s, inFile = true)
      case sa: StringAttachment =>
        AttachmentDto(sa.id.value, sa.mimeType, sa.value.s, inFile = false)
    }
    val comments = items.collect { case c: LineComment => c.text }
    val figmaRefs = items.collect { case fr: FigmaRef => FigmaRefDto(fr.fileKey.s, fr.nodeId.s) }
    val url = items.collectFirst { case u: URLDescription => u.url.toExternalForm }
    if descr.isEmpty && terms.isEmpty && options.isEmpty && authors.isEmpty && attachments.isEmpty &&
      comments.isEmpty && figmaRefs.isEmpty && url.isEmpty
    then None
    else Some(MetaDto(descr, terms, options, authors, attachments, comments, figmaRefs, url))

  private def serializeField(f: Field): FieldDto =
    FieldDto(f.id.value, serializeTypeExpr(f.typeEx), briefOf(f.metadata), metaOf(f.metadata))

  private def serializeMethod(m: Method): MethodDto =
    MethodDto(
      m.id.value,
      serializeTypeExpr(m.typeEx),
      m.args.map(a => MethodArgDto(a.name, serializeTypeExpr(a.typeEx))),
      briefOf(m.metadata)
    )

  // A9: a Function/Saga `requires`/`returns` value becomes an ArgDto — a type ref (preferred) or
  // a deprecated inline field list.
  private def argDto(value: Option[TypeRef | Aggregation]): Option[ArgDto] = value.map {
    case tr: TypeRef      => ArgDto(ref = Some(tr.format))
    case agg: Aggregation => ArgDto(fields = agg.fields.map(serializeField))
  }

  private def serializeTypeExpr(te: TypeExpression): TypeExprDto = te match
    // Emit the canonical String(0,255) bounds explicitly (mirroring the defaults
    // parseJson applies) so an unbounded String is a JSON fixed point: json1==json2.
    case String_(_, min, max) => StringDto(Some(min.getOrElse(0L)), Some(max.getOrElse(255L)))
    case UniqueId(_, p)       => IdDto(Some(path(p)))
    case Currency(_, c)       => CurrencyDto(Some(c))
    case Pattern(_, ps)       => PatternDto(ps.map(_.s))
    case URI(_, scheme)       => URIDto(scheme.map(_.s))
    case Blob(_, bk)          => BlobDto(Some(bk.toString))
    case ZonedDate(_, z)      => ZonedDto("ZonedDate", z.map(_.s))
    case ZonedDateTime(_, z)  => ZonedDto("ZonedDateTime", z.map(_.s))
    case _: Bool              => PredefDto("Boolean")
    case Decimal(_, w, f)     => DecimalDto(Some(w), Some(f))
    case RangeType(_, mn, mx) => RangeDto(Some(mn), Some(mx))
    case Enumeration(_, es)   => EnumDto(es.toSeq.map(e => EnumeratorDto(e.id.value, e.enumVal)))
    case Alternation(_, of)   => AlternationDto(of.toSeq.map(a => path(a.pathId)))
    case AliasedTypeExpression(_, _, p) => AliasDto(path(p))
    case Sequence(_, of)                => CollectionDto("Sequence", serializeTypeExpr(of))
    case s: Set                         => CollectionDto("Set", serializeTypeExpr(s.of))
    case Graph(_, of)                   => CollectionDto("Graph", serializeTypeExpr(of))
    case Replica(_, of)                 => CollectionDto("Replica", serializeTypeExpr(of))
    case Mapping(_, from, to) => MappingDto(serializeTypeExpr(from), serializeTypeExpr(to))
    case Table(_, of, dims)   => TableDto(serializeTypeExpr(of), dims)
    case EntityReferenceTypeExpression(_, e) => EntityRefDto(Some(path(e)))
    case Optional(_, inner)                  => CardinalityDto("optional", serializeTypeExpr(inner))
    case ZeroOrMore(_, inner) => CardinalityDto("zeroOrMore", serializeTypeExpr(inner))
    case OneOrMore(_, inner)  => CardinalityDto("oneOrMore", serializeTypeExpr(inner))
    case SpecificRange(_, inner, mn, mx) =>
      CardinalityDto("range", serializeTypeExpr(inner), Some(mn), Some(mx))
    case a: AggregateTypeExpression =>
      RecordDto(
        a.fields.map(serializeField),
        a.methods.map(serializeMethod),
        commentsOf(a.contents),
        Some(aggregateFlavour(a))
      )
    case p: PredefinedType => PredefDto(p.getClass.getSimpleName.replace("$", ""))

  /** What `RecordDto.aggregate` carries: the RIDDL type keyword of a use-case aggregate, or
    * "aggregation" for a bare `{…}`, which has no keyword. Without this every aggregate read back
    * as a `record`, so `type X is {…}` returned as `record X is {…}`.
    */
  private def aggregateFlavour(a: AggregateTypeExpression): String = a match
    case aucte: AggregateUseCaseTypeExpression => aucte.usecase.useCase.toLowerCase
    case _                                     => "aggregation"

  /** `Statements` is `Statement | Comment`, so a comment between two statements is part of the list
    * and is serialized in place rather than dropped.
    */
  private def serializeStatements(c: Contents[Statements]): Seq[StatementDto] =
    c.toSeq.collect {
      case s: Statement      => serializeStatement(s)
      case lc: LineComment   => CommentStmtDto(lc.text)
      case ic: InlineComment => CommentStmtDto(ic.lines.mkString("\n"), inline = true)
    }

  private def serializeStatement(s: Statement): StatementDto = s match
    case PromptStatement(_, what) => PromptStmtDto(what.s)
    case ErrorStatement(_, msg)   => ErrorStmtDto(msg.s)
    case LetStatement(_, id, tr, e) =>
      LetStmtDto(id.value, tr.map(t => path(t.pathId)), serializeValue(e))
    case CodeStatement(_, lang, body) => CodeStmtDto(lang.s, body)
    case RequireStatement(_, cond) =>
      cond match
        case ls: LiteralString     => RequireStmtDto(Some(ls.s), None)
        case ir: InvariantRef      => RequireStmtDto(None, Some(path(ir.pathId)))
        case be: BooleanExpression => RequireStmtDto(None, None, Some(serializeValue(be))) // A28
    case SetStatement(_, field, value) =>
      field match
        case fr: FieldRef => SetStmtDto(Some(path(fr.pathId)), None, serializeValue(value))
        case sr: StateRef => SetStmtDto(None, Some(path(sr.pathId)), serializeValue(value))
    case SendStatement(_, msg, portlet) =>
      val (pp, pk) = portletRef(portlet); SendStmtDto(serializeMsgOperand(msg), pp, pk)
    case MorphStatement(_, entity, state, value) =>
      // A9b/A54: morph value is a RecordRef (serialized as a record-kinded MessageRefDto) or a
      // Constructor.
      MorphStmtDto(path(entity.pathId), path(state.pathId), serializeRecordOperand(value))
    case BecomeStatement(_, entity, handler) =>
      BecomeStmtDto(path(entity.pathId), path(handler.pathId))
    case TellStatement(_, msg, proc) =>
      val (pp, pk) = processorRef(proc); TellStmtDto(serializeMsgOperand(msg), pp, pk)
    case YieldStatement(_, msg) => YieldStmtDto(serializeMsgOperand(msg))
    case WhenStatement(_, cond, thenS, elseS, negated) =>
      cond match
        case ls: LiteralString =>
          WhenStmtDto(
            Some(ls.s),
            None,
            negated,
            serializeStatements(thenS),
            serializeStatements(elseS)
          )
        case id: Identifier =>
          WhenStmtDto(
            None,
            Some(id.value),
            negated,
            serializeStatements(thenS),
            serializeStatements(elseS)
          )
        case be: BooleanExpression => // A28: structured boolean-expression condition
          WhenStmtDto(
            None,
            None,
            negated,
            serializeStatements(thenS),
            serializeStatements(elseS),
            Some(serializeValue(be))
          )
        case vr: ValueRef => // A17: bare boolean value reference -> structured `expression` field
          WhenStmtDto(
            None,
            None,
            negated,
            serializeStatements(thenS),
            serializeStatements(elseS),
            Some(serializeValue(vr))
          )
        case pv: PromptValue => // an AI-evaluated condition, `when prompt("...")`
          WhenStmtDto(
            None,
            None,
            negated,
            serializeStatements(thenS),
            serializeStatements(elseS),
            Some(serializeValue(pv))
          )
    case MatchStatement(_, expr, cases, default) =>
      MatchStmtDto(
        serializeValue(expr), // A29: subject is a MatchSubject (all Value arms)
        cases.map(c =>
          MatchCaseDto(
            serializeMatchPattern(c.pattern),
            c.guard.map(serializeValue),
            serializeStatements(c.statements)
          )
        ),
        serializeStatements(default)
      )
    case ForeachStatement(_, element, collection, doStatements) =>
      collection match
        case fr: FieldRef =>
          ForeachStmtDto(
            element.value,
            Some(path(fr.pathId)),
            None,
            serializeStatements(doStatements)
          )
        case id: Identifier =>
          ForeachStmtDto(element.value, None, Some(id.value), serializeStatements(doStatements))
    case PutStatement(_, value, output) =>
      PutStmtDto(serializeValue(value), path(output.pathId))
    case ReturnStatement(_, value) =>
      ReturnStmtDto(serializeValue(value))

  // A54: AST Value -> ValueDto.
  private def serializeValue(v: Value): ValueDto = v match
    case ls: LiteralString => LiteralValueDto(ls.s)
    case pv: PromptValue   => PromptValueDto(pv.prompt.s)
    case vr: ValueRef      => ValueRefDto(path(vr.path))
    case gv: GetValue =>
      gv.source match
        case ir: InputRef => GetValueDto("input", Some(ir.keyword), path(ir.pathId))
        case sr: StateRef => GetValueDto("state", None, path(sr.pathId))
    case c: Constructor     => serializeConstructor(c)
    case call: Call         => serializeCall(call) // A24
    case bl: BooleanLiteral => BooleanLiteralDto(bl.value)
    case ce: ComparisonExpression =>
      ComparisonDto(ce.op.symbol, serializeComparand(ce.left), serializeComparand(ce.right))
    case le: LogicalExpression =>
      LogicalDto(le.op.symbol, serializeValue(le.left), serializeValue(le.right))
    case ne: NotExpression => NotDto(serializeValue(ne.expr))

  // A29: a match-case pattern -> MatchPatternDto.
  private def serializeMatchPattern(p: MatchPattern): MatchPatternDto = p match
    case tp: TypePattern =>
      TypePatternDto(path(tp.typeRef.pathId), Some(tp.typeRef.keyword))
    case cp: ComparisonPattern =>
      ComparisonPatternDto(cp.op.symbol, serializeComparand(cp.comparand))
    case lp: LiteralPattern => LiteralPatternDto(lp.literal.s)

  // A28: a comparison operand (Comparand = ValueRef | GetValue | ConstantRef) -> ValueDto.
  private def serializeComparand(c: Comparand): ValueDto = c match
    case vr: ValueRef    => ValueRefDto(path(vr.path))
    case cr: ConstantRef => ConstantRefDto(path(cr.pathId))
    case gv: GetValue =>
      gv.source match
        case ir: InputRef => GetValueDto("input", Some(ir.keyword), path(ir.pathId))
        case sr: StateRef => GetValueDto("state", None, path(sr.pathId))

  // A54: AST Constructor -> ConstructorValueDto.
  private def serializeConstructor(c: Constructor): ConstructorValueDto =
    val refKind = c.ref match
      case _: CommandRef => "command"
      case _: EventRef   => "event"
      case _: QueryRef   => "query"
      case _: ResultRef  => "result"
      case _: RecordRef  => "record"
    ConstructorValueDto(
      refKind,
      path(c.ref.pathId),
      c.args.map(a => ConstructorArgDto(a.name.map(_.value), serializeValue(a.value)))
    )

  // A24: AST Call -> CallValueDto.
  private def serializeCall(c: Call): CallValueDto =
    CallValueDto(
      path(c.function.pathId),
      c.args.map(a => ConstructorArgDto(a.name.map(_.value), serializeValue(a.value)))
    )

  // A54: a message operand — a bare ref or an inline constructor.
  private def serializeMsgOperand(m: MessageRef | Constructor): MsgOperandDto = m match
    case mr: MessageRef => messageRefDto(mr)
    case c: Constructor => serializeConstructor(c)

  // A54: a record operand for `morph … with` — a bare record ref or an inline constructor.
  private def serializeRecordOperand(m: RecordRef | Constructor): MsgOperandDto = m match
    case rr: RecordRef  => MessageRefDto(path(rr.pathId), "record")
    case c: Constructor => serializeConstructor(c)

  private def serializeInteraction(i: Interaction): InteractionDto = i match
    case VagueInteraction(_, from, rel, to, _) => VagueIxnDto(from.s, rel.s, to.s)
    case SendMessageInteraction(_, from, msg, to, _) =>
      val (pp, pk) = processorRef(to); SendMessageIxnDto(refDto(from), messageRefDto(msg), pp, pk)
    case ArbitraryInteraction(_, from, rel, to, _) =>
      ArbitraryIxnDto(refDto(from), rel.s, refDto(to))
    case SelfInteraction(_, from, rel, _) => SelfIxnDto(refDto(from), rel.s)
    case FocusOnGroupInteraction(_, user, group, _) =>
      FocusOnGroupIxnDto(path(user.pathId), path(group.pathId))
    case DirectUserToURLInteraction(_, user, url, _) =>
      DirectToURLIxnDto(path(user.pathId), url.toExternalForm)
    case ShowOutputInteraction(_, output, rel, user, _) =>
      ShowOutputIxnDto(path(output.pathId), rel.s, path(user.pathId))
    case SelectInputInteraction(_, user, input, _) =>
      SelectInputIxnDto(path(user.pathId), path(input.pathId))
    case TakeInputInteraction(_, user, input, _) =>
      TakeInputIxnDto(path(user.pathId), path(input.pathId))
    case RefusalInteraction(_, from, user, reason, _) =>
      RefusalIxnDto(refDto(from), path(user.pathId), reason.s)
    case ParallelInteractions(_, contents, _)   => ParallelIxnDto(serIxns(contents))
    case SequentialInteractions(_, contents, _) => SequentialIxnDto(serIxns(contents))
    case OptionalInteractions(_, contents, _)   => OptionalIxnDto(serIxns(contents))

  private def serIxns(c: Contents[InteractionContainerContents]): Seq[InteractionDto] =
    c.toSeq.collect { case i: Interaction => serializeInteraction(i) }

  private def serializeUserStory(us: UserStory): UserStoryDto =
    UserStoryDto(path(us.user.pathId), us.capability.s, us.benefit.s)

  private def serializePutOut(p: TypeRef | ConstantRef | LiteralString): PutOutDto = p match
    case tr: TypeRef       => PutOutDto("type", path(tr.pathId), Some(tr.keyword))
    case cr: ConstantRef   => PutOutDto("constant", path(cr.pathId), None)
    case ls: LiteralString => PutOutDto("literal", ls.s, None)

  // A9b: a MessageRef is one of the 4 messages (a record is not a message).
  private def messageRefDto(mr: MessageRef): MessageRefDto = mr match
    case CommandRef(_, p) => MessageRefDto(path(p), "command")
    case EventRef(_, p)   => MessageRefDto(path(p), "event")
    case QueryRef(_, p)   => MessageRefDto(path(p), "query")
    case ResultRef(_, p)  => MessageRefDto(path(p), "result")

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
