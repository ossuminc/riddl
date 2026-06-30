/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Contents}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.{Message, Messages}
import com.ossuminc.riddl.utils.URL

import scala.collection.mutable

/** Pure, Native-safe construction of a RIDDL [[AST.Root]] from the JSON wire model ([[JsonModel]]).
  * No I/O. References are emitted as `PathIdentifier`s and left for
  * `ResolutionPass`/`ValidationPass` to resolve — the builder only guarantees structural
  * correctness and supplies RIDDL's required defaults.
  *
  * Builder-level errors (the few things the AST cannot express or default — a missing `Id.entity`,
  * an empty `Enum`/`Pattern`, an unknown `kind`) are collected and returned as `Left(Messages)`;
  * everything else becomes a structurally-valid `Root`.
  */
object JsonAstBuilder:

  import JsonModel.*

  /** Build a `Root` from the wire model, or the accumulated builder errors. */
  def build(dto: RootDto): Either[Messages, Root] =
    given ctx: Ctx = new Ctx
    val domains = dto.domains.map(buildDomain)
    val modules = dto.modules.map(buildModule)
    val root = Root(At(), contentsOf[RootContents](domains, modules))
    if ctx.errors.isEmpty then Right(root) else Left(ctx.errors.toList)
  end build

  private def buildModule(m: ModuleDto)(using Ctx): Module =
    val authors = m.authors.map(buildAuthor)
    val domains = m.domains.map(buildDomain)
    Module(At(), ident(m.name), contentsOf[ModuleContents](authors, domains), meta(m.brief))

  /** Mutable error sink threaded through construction. */
  private final class Ctx:
    val errors: mutable.ListBuffer[Message] = mutable.ListBuffer.empty
    def err(message: String): Unit = errors += Messages.error(message)

  /** Collect heterogeneous child groups (each a subtype of `T`) into a typed `Contents[T]`. `Seq[?
    * <: T]` keeps each call-site group correctly typed.
    */
  private def contentsOf[T <: RiddlValue](groups: Seq[? <: T]*): Contents[T] =
    val buf = mutable.ArrayBuffer.empty[T]
    groups.foreach(g => buf ++= g)
    Contents[T](buf.toSeq*)

  /** A brief, if present, as a one-element metadata block (otherwise empty). */
  private def meta(brief: Option[String]): Contents[MetaData] =
    brief match
      case Some(b) => Contents[MetaData](BriefDescription(At(), LiteralString(At(), b)))
      case None    => Contents.empty[MetaData]()

  private def ident(name: String): Identifier = Identifier(At(), name)

  /** A dotted reference string -> PathIdentifier segments. */
  private def pathId(ref: String): PathIdentifier =
    PathIdentifier(At(), ref.split('.').iterator.filter(_.nonEmpty).toSeq)

  // ---------------------------------------------------------------------------
  // Definitions
  // ---------------------------------------------------------------------------

  private def buildDomain(d: DomainDto)(using Ctx): Domain =
    val authors = d.authors.map(buildAuthor)
    val users = d.users.map(buildUser)
    val types = d.types.map(buildType)
    val sagas = d.sagas.map(buildSaga)
    val epics = d.epics.map(buildEpic)
    val subdomains = d.domains.map(buildDomain)
    val contexts = d.contexts.map(buildContext)
    Domain(
      At(),
      ident(d.name),
      contentsOf[DomainContents](authors, users, types, sagas, epics, subdomains, contexts),
      meta(d.brief)
    )

  private def buildUser(u: UserDto): User =
    User(At(), ident(u.name), LiteralString(At(), u.isA), meta(u.brief))

  private def buildConstant(c: ConstantDto)(using Ctx): Constant =
    Constant(
      At(),
      ident(c.name),
      buildTypeExpr(c.`type`),
      LiteralString(At(), c.value),
      meta(c.brief)
    )

  private def buildAuthor(a: AuthorDto): Author =
    Author(
      At(),
      ident(a.name),
      LiteralString(At(), a.fullName),
      LiteralString(At(), a.email),
      a.organization.map(LiteralString(At(), _)),
      a.title.map(LiteralString(At(), _)),
      None,
      Contents.empty[MetaData]()
    )

  private def buildType(t: TypeDefDto)(using Ctx): Type =
    Type(At(), ident(t.name), buildTypeExpr(t.typeExpression), meta(t.brief))

  private def buildContext(c: ContextDto)(using Ctx): Context =
    val types = c.types.map(buildType)
    val constants = c.constants.map(buildConstant)
    val commands = c.commands.map(m => buildMessage(m, AggregateUseCase.CommandCase))
    val events = c.events.map(m => buildMessage(m, AggregateUseCase.EventCase))
    val queries = c.queries.map(m => buildMessage(m, AggregateUseCase.QueryCase))
    val results = c.results.map(m => buildMessage(m, AggregateUseCase.ResultCase))
    val entities = c.entities.map(buildEntity)
    val functions = c.functions.map(buildFunction)
    val adaptors = c.adaptors.map(buildAdaptor)
    val streamlets = c.streamlets.map(buildStreamlet)
    val projectors = c.projectors.map(buildProjector)
    val repositories = c.repositories.map(buildRepository)
    val connectors = c.connectors.map(buildConnector)
    val relationships = c.relationships.map(buildRelationship)
    val sagas = c.sagas.map(buildSaga)
    val groups = c.groups.map(buildGroup)
    val handlers = c.handlers.map(buildHandler)
    Context(
      At(),
      ident(c.name),
      contentsOf[ContextContents](
        types,
        constants,
        commands,
        events,
        queries,
        results,
        entities,
        functions,
        adaptors,
        streamlets,
        projectors,
        repositories,
        connectors,
        relationships,
        sagas,
        groups,
        handlers
      ),
      meta(c.brief)
    )

  /** A message (command/event/query/result) is a `Type` whose expression is an aggregate tagged
    * with the appropriate use case.
    */
  private def buildMessage(m: MessageDto, useCase: AggregateUseCase)(using Ctx): Type =
    val fields = m.fields.map(buildField)
    val typEx = AggregateUseCaseTypeExpression(At(), useCase, Contents[AggregateContents](fields*))
    Type(At(), ident(m.name), typEx, meta(m.brief))

  private def buildField(f: FieldDto)(using Ctx): Field =
    Field(At(), ident(f.name), buildTypeExpr(f.`type`), meta(f.brief))

  /** A field list as an optional Aggregation (None when empty) — used for function and saga
    * input/output.
    */
  private def aggregationOf(fields: Seq[FieldDto])(using Ctx): Option[Aggregation] =
    if fields.isEmpty then None
    else Some(Aggregation(At(), Contents[AggregateContents](fields.map(buildField)*)))

  private def buildMethod(m: MethodDto)(using Ctx): Method =
    val args = m.args.map(a => MethodArgument(At(), a.name, buildTypeExpr(a.`type`)))
    Method(At(), ident(m.name), buildTypeExpr(m.`type`), args, meta(m.brief))

  private def buildEntity(e: EntityDto)(using Ctx): Entity =
    val types = e.types.map(buildType)
    val constants = e.constants.map(buildConstant)
    val states = e.state.toSeq.map(buildState)
    val functions = e.functions.map(buildFunction)
    val handlers = e.handlers.map(buildHandler)
    val invariants = e.invariants.map(buildInvariant)
    Entity(
      At(),
      ident(e.name),
      contentsOf[EntityContents](types, constants, states, functions, handlers, invariants),
      meta(e.brief)
    )

  /** A state references a record type; RIDDL holds no fields in a state. */
  private def buildState(s: StateDto): State =
    State(
      At(),
      ident(s.name),
      TypeRef(At(), "record", pathId(s.recordType)),
      Contents.empty[StateContents](),
      Contents.empty[MetaData]()
    )

  private def buildHandler(h: HandlerDto)(using Ctx): Handler =
    val clauses = h.onClauses.map(buildOnClause)
    Handler(At(), ident(h.name), contentsOf[HandlerContents](clauses), meta(h.brief))

  private def buildOnClause(oc: OnClauseDto)(using ctx: Ctx): OnClause =
    val statements = buildStatements(oc.statements)
    oc.kind match
      case "message" =>
        oc.message match
          case Some(mr) =>
            OnMessageClause(At(), messageRef(mr), None, statements, Contents.empty[MetaData]())
          case None =>
            ctx.err("on-clause of kind 'message' requires a 'message' reference")
            OnMessageClause(
              At(),
              CommandRef(At(), PathIdentifier.empty),
              None,
              statements,
              Contents.empty[MetaData]()
            )
      case "init"  => OnInitializationClause(At(), statements, Contents.empty[MetaData]())
      case "other" => OnOtherClause(At(), statements, Contents.empty[MetaData]())
      case "term"  => OnTerminationClause(At(), statements, Contents.empty[MetaData]())
      case other =>
        ctx.err(s"unknown on-clause kind '$other' (expected message|init|other|term)")
        OnOtherClause(At(), statements, Contents.empty[MetaData]())
    end match
  end buildOnClause

  private def messageRef(mr: MessageRefDto)(using ctx: Ctx): MessageRef =
    mr.kind match
      case "command" => CommandRef(At(), pathId(mr.ref))
      case "event"   => EventRef(At(), pathId(mr.ref))
      case "query"   => QueryRef(At(), pathId(mr.ref))
      case "result"  => ResultRef(At(), pathId(mr.ref))
      case "record"  => RecordRef(At(), pathId(mr.ref))
      case other =>
        ctx.err(s"unknown message kind '$other' (expected command|event|query|result|record)")
        CommandRef(At(), pathId(mr.ref))

  private def buildInvariant(i: InvariantDto): Invariant =
    Invariant(At(), ident(i.name), Some(LiteralString(At(), i.condition)), meta(i.brief))

  // ---------------------------------------------------------------------------
  // Functions and statements (Phase 3)
  // ---------------------------------------------------------------------------

  private def buildFunction(f: FunctionDto)(using Ctx): Function =
    val types = f.types.map(buildType)
    val statements = f.statements.map(buildStatement)
    val functions = f.functions.map(buildFunction)
    Function(
      At(),
      ident(f.name),
      aggregationOf(f.input),
      aggregationOf(f.output),
      contentsOf[FunctionContents](types, statements, functions),
      meta(f.brief)
    )

  private def buildSagaStep(st: SagaStepDto)(using Ctx): SagaStep =
    SagaStep(
      At(),
      ident(st.name),
      buildStatements(st.`do`),
      buildStatements(st.undo),
      meta(st.brief)
    )

  private def buildSaga(s: SagaDto)(using Ctx): Saga =
    val types = s.types.map(buildType)
    val steps = s.steps.map(buildSagaStep)
    Saga(
      At(),
      ident(s.name),
      aggregationOf(s.input),
      aggregationOf(s.output),
      contentsOf[SagaContents](types, steps),
      meta(s.brief)
    )

  // ---------------------------------------------------------------------------
  // Epics, use cases, interactions (Phase 7)
  // ---------------------------------------------------------------------------

  private def buildUserStory(us: UserStoryDto): UserStory =
    UserStory(
      At(),
      UserRef(At(), pathId(us.user)),
      LiteralString(At(), us.capability),
      LiteralString(At(), us.benefit)
    )

  /** A generic definition reference for an interaction's from/to. */
  private def buildRef(r: RefDto)(using ctx: Ctx): Reference[Definition] =
    r.kind match
      case "user"      => UserRef(At(), pathId(r.path))
      case "entity"    => EntityRef(At(), pathId(r.path))
      case "context"   => ContextRef(At(), pathId(r.path))
      case "group"     => GroupRef(At(), "group", pathId(r.path))
      case "output"    => OutputRef(At(), "output", pathId(r.path))
      case "input"     => InputRef(At(), "input", pathId(r.path))
      case "adaptor"   => AdaptorRef(At(), pathId(r.path))
      case "projector" => ProjectorRef(At(), pathId(r.path))
      case other =>
        ctx.err(s"unknown reference kind '$other' for an interaction")
        UserRef(At(), pathId(r.path))

  private def buildInteraction(i: InteractionDto)(using ctx: Ctx): Interaction =
    val nm = Contents.empty[MetaData]()
    i match
      case VagueIxnDto(from, rel, to) =>
        VagueInteraction(
          At(),
          LiteralString(At(), from),
          LiteralString(At(), rel),
          LiteralString(At(), to),
          nm
        )
      case SendMessageIxnDto(from, msg, to, proc) =>
        SendMessageInteraction(At(), buildRef(from), messageRef(msg), processorRef(to, proc), nm)
      case ArbitraryIxnDto(from, rel, to) =>
        ArbitraryInteraction(At(), buildRef(from), LiteralString(At(), rel), buildRef(to), nm)
      case SelfIxnDto(from, rel) =>
        SelfInteraction(At(), buildRef(from), LiteralString(At(), rel), nm)
      case FocusOnGroupIxnDto(user, group) =>
        FocusOnGroupInteraction(
          At(),
          UserRef(At(), pathId(user)),
          GroupRef(At(), "group", pathId(group)),
          nm
        )
      case DirectToURLIxnDto(user, url) =>
        DirectUserToURLInteraction(At(), UserRef(At(), pathId(user)), URL(url), nm)
      case ShowOutputIxnDto(output, rel, user) =>
        ShowOutputInteraction(
          At(),
          OutputRef(At(), "output", pathId(output)),
          LiteralString(At(), rel),
          UserRef(At(), pathId(user)),
          nm
        )
      case SelectInputIxnDto(user, input) =>
        SelectInputInteraction(
          At(),
          UserRef(At(), pathId(user)),
          InputRef(At(), "input", pathId(input)),
          nm
        )
      case TakeInputIxnDto(user, input) =>
        TakeInputInteraction(
          At(),
          UserRef(At(), pathId(user)),
          InputRef(At(), "input", pathId(input)),
          nm
        )
      case ParallelIxnDto(ixns) =>
        ParallelInteractions(
          At(),
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
      case SequentialIxnDto(ixns) =>
        SequentialInteractions(
          At(),
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
      case OptionalIxnDto(ixns) =>
        OptionalInteractions(
          At(),
          contentsOf[InteractionContainerContents](ixns.map(buildInteraction)),
          nm
        )
    end match
  end buildInteraction

  private def buildUseCase(u: UseCaseDto)(using Ctx): UseCase =
    UseCase(
      At(),
      ident(u.name),
      buildUserStory(u.userStory),
      contentsOf[UseCaseContents](u.interactions.map(buildInteraction)),
      meta(u.brief)
    )

  private def buildEpic(e: EpicDto)(using Ctx): Epic =
    val types = e.types.map(buildType)
    val useCases = e.useCases.map(buildUseCase)
    val shownBy = if e.shownBy.isEmpty then Nil else Seq(ShownBy(At(), e.shownBy.map(u => URL(u))))
    Epic(
      At(),
      ident(e.name),
      buildUserStory(e.userStory),
      contentsOf[EpicContents](types, shownBy, useCases),
      meta(e.brief)
    )

  // ---------------------------------------------------------------------------
  // UI groups (Phase 8)
  // ---------------------------------------------------------------------------

  private def buildPutOut(p: PutOutDto)(using ctx: Ctx): TypeRef | ConstantRef | LiteralString =
    p.kind match
      case "type"     => TypeRef(At(), p.keyword.getOrElse("type"), pathId(p.value))
      case "constant" => ConstantRef(At(), pathId(p.value))
      case "literal"  => LiteralString(At(), p.value)
      case other =>
        ctx.err(s"unknown output putOut kind '$other' (expected type|constant|literal)")
        LiteralString(At(), p.value)

  private def buildInput(i: InputDto)(using Ctx): Input =
    Input(
      At(),
      i.nounAlias.getOrElse("input"),
      ident(i.name),
      i.verbAlias.getOrElse("acquires"),
      TypeRef(At(), "type", pathId(i.takeIn)),
      contentsOf[OccursInInput](i.inputs.map(buildInput)),
      meta(i.brief)
    )

  private def buildOutput(o: OutputDto)(using Ctx): Output =
    Output(
      At(),
      o.nounAlias.getOrElse("output"),
      ident(o.name),
      o.verbAlias.getOrElse("displays"),
      buildPutOut(o.putOut),
      contentsOf[OccursInOutput](o.outputs.map(buildOutput)),
      meta(o.brief)
    )

  private def buildContainedGroup(cg: ContainedGroupDto): ContainedGroup =
    ContainedGroup(At(), ident(cg.name), GroupRef(At(), "group", pathId(cg.group)), meta(cg.brief))

  private def buildGroup(g: GroupDto)(using Ctx): Group =
    val groups = g.groups.map(buildGroup)
    val contained = g.containedGroups.map(buildContainedGroup)
    val inputs = g.inputs.map(buildInput)
    val outputs = g.outputs.map(buildOutput)
    Group(
      At(),
      g.alias.getOrElse("group"),
      ident(g.name),
      contentsOf[OccursInGroup](groups, contained, inputs, outputs),
      meta(g.brief)
    )

  private def buildStatements(stmts: Seq[StatementDto])(using Ctx): Contents[Statements] =
    Contents[Statements](stmts.map(buildStatement)*)

  private def buildStatement(s: StatementDto)(using ctx: Ctx): Statement =
    s match
      case PromptStmtDto(text)   => PromptStatement(At(), LiteralString(At(), text))
      case ErrorStmtDto(message) => ErrorStatement(At(), LiteralString(At(), message))
      case LetStmtDto(name, t, expression) =>
        LetStatement(
          At(),
          ident(name),
          t.map(p => TypeRef(At(), "type", pathId(p))),
          LiteralString(At(), expression)
        )
      case CodeStmtDto(language, body) => CodeStatement(At(), LiteralString(At(), language), body)
      case RequireStmtDto(condition, invariant) =>
        val cond: LiteralString | InvariantRef = invariant match
          case Some(name) => InvariantRef(At(), pathId(name))
          case None =>
            condition match
              case Some(c) => LiteralString(At(), c)
              case None =>
                ctx.err("require statement needs a 'condition' or an 'invariant'")
                LiteralString(At(), "")
        RequireStatement(At(), cond)
      case SetStmtDto(field, state, value) =>
        val target: FieldRef | StateRef = (field, state) match
          case (Some(f), _)     => FieldRef(At(), pathId(f))
          case (None, Some(st)) => StateRef(At(), pathId(st))
          case (None, None) =>
            ctx.err("set statement needs a 'field' or a 'state' target")
            FieldRef(At(), PathIdentifier.empty)
        SetStatement(At(), target, LiteralString(At(), value))
      case SendStmtDto(message, to, portlet) =>
        SendStatement(At(), messageRef(message), portletRef(to, portlet))
      case MorphStmtDto(entity, state, value) =>
        MorphStatement(
          At(),
          EntityRef(At(), pathId(entity)),
          StateRef(At(), pathId(state)),
          messageRef(value)
        )
      case BecomeStmtDto(entity, handler) =>
        BecomeStatement(At(), EntityRef(At(), pathId(entity)), HandlerRef(At(), pathId(handler)))
      case TellStmtDto(message, to, processor) =>
        TellStatement(At(), messageRef(message), processorRef(to, processor))
      case ReplyStmtDto(message) => ReplyStatement(At(), messageRef(message))
      case WhenStmtDto(condition, conditionId, negated, thenS, elseS) =>
        val cond: LiteralString | Identifier = conditionId match
          case Some(id) => ident(id)
          case None     => LiteralString(At(), condition.getOrElse(""))
        WhenStatement(At(), cond, buildStatements(thenS), buildStatements(elseS), negated)
      case MatchStmtDto(expression, cases, default) =>
        MatchStatement(
          At(),
          LiteralString(At(), expression),
          cases.map(buildMatchCase),
          buildStatements(default)
        )
  end buildStatement

  private def buildMatchCase(c: MatchCaseDto)(using Ctx): MatchCase =
    MatchCase(At(), LiteralString(At(), c.pattern), buildStatements(c.statements))

  private def portletRef(path: String, kind: String)(using ctx: Ctx): PortletRef[Portlet] =
    kind match
      case "inlet"  => InletRef(At(), pathId(path))
      case "outlet" => OutletRef(At(), pathId(path))
      case other =>
        ctx.err(s"unknown portlet kind '$other' (expected inlet|outlet)")
        InletRef(At(), pathId(path))

  private def processorRef(path: String, kind: String)(using ctx: Ctx): ProcessorRef[Processor[?]] =
    kind match
      case "entity"     => EntityRef(At(), pathId(path))
      case "context"    => ContextRef(At(), pathId(path))
      case "projector"  => ProjectorRef(At(), pathId(path))
      case "repository" => RepositoryRef(At(), pathId(path))
      case "adaptor"    => AdaptorRef(At(), pathId(path))
      case other =>
        ctx.err(
          s"unknown processor kind '$other' (expected entity|context|projector|repository|adaptor)"
        )
        EntityRef(At(), pathId(path))

  // ---------------------------------------------------------------------------
  // Streaming & integration (Phase 4)
  // ---------------------------------------------------------------------------

  private def adaptorDirection(s: String)(using ctx: Ctx): AdaptorDirection =
    s match
      case "inbound"  => InboundAdaptor(At())
      case "outbound" => OutboundAdaptor(At())
      case other =>
        ctx.err(s"unknown adaptor direction '$other' (expected inbound|outbound)")
        InboundAdaptor(At())

  private def buildAdaptor(a: AdaptorDto)(using Ctx): Adaptor =
    val types = a.types.map(buildType)
    val constants = a.constants.map(buildConstant)
    val functions = a.functions.map(buildFunction)
    val handlers = a.handlers.map(buildHandler)
    Adaptor(
      At(),
      ident(a.name),
      adaptorDirection(a.direction),
      ContextRef(At(), pathId(a.context)),
      contentsOf[AdaptorContents](types, constants, functions, handlers),
      meta(a.brief)
    )

  private def streamletShape(s: String)(using ctx: Ctx): StreamletShape =
    s match
      case "source" => Source(At())
      case "sink"   => Sink(At())
      case "flow"   => Flow(At())
      case "merge"  => Merge(At())
      case "split"  => Split(At())
      case "router" => Router(At())
      case "void"   => Void(At())
      case other =>
        ctx.err(s"unknown streamlet shape '$other' (source|sink|flow|merge|split|router|void)")
        Void(At())

  private def buildInlet(p: PortletDto): Inlet =
    Inlet(At(), ident(p.name), TypeRef(At(), "type", pathId(p.`type`)), meta(p.brief))

  private def buildOutlet(p: PortletDto): Outlet =
    Outlet(At(), ident(p.name), TypeRef(At(), "type", pathId(p.`type`)), meta(p.brief))

  private def buildConnector(c: ConnectorDto): Connector =
    Connector(
      At(),
      ident(c.name),
      OutletRef(At(), pathId(c.from)),
      InletRef(At(), pathId(c.to)),
      meta(c.brief)
    )

  private def buildStreamlet(s: StreamletDto)(using Ctx): Streamlet =
    val inlets = s.inlets.map(buildInlet)
    val outlets = s.outlets.map(buildOutlet)
    val connectors = s.connectors.map(buildConnector)
    val types = s.types.map(buildType)
    val handlers = s.handlers.map(buildHandler)
    Streamlet(
      At(),
      ident(s.name),
      streamletShape(s.shape),
      contentsOf[StreamletContents](types, inlets, outlets, connectors, handlers),
      meta(s.brief)
    )

  private def relationshipCardinality(s: String)(using ctx: Ctx): RelationshipCardinality =
    s match
      case "1:1" | "OneToOne"   => RelationshipCardinality.OneToOne
      case "1:N" | "OneToMany"  => RelationshipCardinality.OneToMany
      case "N:1" | "ManyToOne"  => RelationshipCardinality.ManyToOne
      case "N:N" | "ManyToMany" => RelationshipCardinality.ManyToMany
      case other =>
        ctx.err(s"unknown relationship cardinality '$other' (expected 1:1|1:N|N:1|N:N)")
        RelationshipCardinality.OneToOne

  private def buildRelationship(r: RelationshipDto)(using Ctx): Relationship =
    Relationship(
      At(),
      ident(r.name),
      processorRef(r.withProcessor, r.processor),
      relationshipCardinality(r.cardinality),
      r.label.map(LiteralString(At(), _)),
      meta(r.brief)
    )

  private def buildProjector(p: ProjectorDto)(using Ctx): Projector =
    val types = p.types.map(buildType)
    val constants = p.constants.map(buildConstant)
    val functions = p.functions.map(buildFunction)
    val handlers = p.handlers.map(buildHandler)
    val repoRefs = p.repository.toSeq.map(r => RepositoryRef(At(), pathId(r)))
    Projector(
      At(),
      ident(p.name),
      contentsOf[ProjectorContents](types, constants, functions, handlers, repoRefs),
      meta(p.brief)
    )

  private def schemaKind(s: Option[String])(using ctx: Ctx): RepositorySchemaKind =
    s match
      case None => RepositorySchemaKind.Other
      case Some(k) =>
        scala.util.Try(RepositorySchemaKind.valueOf(k)).toOption.getOrElse {
          ctx.err(s"unknown schema kind '$k'")
          RepositorySchemaKind.Other
        }

  private def buildSchema(s: SchemaDto)(using ctx: Ctx): Schema =
    val data = s.data.map { case (k, v) => Identifier(At(), k) -> TypeRef(At(), "type", pathId(v)) }
    val links = s.links.flatMap { case (k, fields) =>
      if fields.sizeIs >= 2 then
        Some(
          Identifier(At(), k) -> (
            FieldRef(At(), pathId(fields(0))),
            FieldRef(At(), pathId(fields(1)))
          )
        )
      else
        ctx.err(s"schema link '$k' needs two field references")
        None
    }
    val indices = s.indices.map(f => FieldRef(At(), pathId(f)))
    Schema(At(), ident(s.name), schemaKind(s.kind), data, links, indices, meta(s.brief))

  private def buildRepository(r: RepositoryDto)(using Ctx): Repository =
    val types = r.types.map(buildType)
    val handlers = r.handlers.map(buildHandler)
    val schemas = r.schema.toSeq.map(buildSchema)
    Repository(
      At(),
      ident(r.name),
      contentsOf[RepositoryContents](types, schemas, handlers),
      meta(r.brief)
    )

  // ---------------------------------------------------------------------------
  // Type expressions (with the defaults table applied here, in the builder)
  // ---------------------------------------------------------------------------

  private def buildTypeExpr(dto: TypeExprDto)(using ctx: Ctx): TypeExpression =
    dto match
      case StringDto(min, max) => String_(At(), Some(min.getOrElse(0L)), Some(max.getOrElse(255L)))

      case IdDto(entity) =>
        entity match
          case Some(e) => UniqueId(At(), pathId(e))
          case None =>
            ctx.err("Id type requires an 'entity' path (it cannot be defaulted)")
            UniqueId(At(), PathIdentifier.empty)

      case PredefDto(kind) =>
        kind match
          case "UUID"        => UUID(At())
          case "Boolean"     => Bool(At())
          case "Date"        => Date(At())
          case "TimeStamp"   => TimeStamp(At())
          case "Integer"     => Integer(At())
          case "Whole"       => Whole(At())
          case "Natural"     => Natural(At())
          case "Number"      => Number(At())
          case "Real"        => Real(At())
          case "UserId"      => UserId(At())
          case "Abstract"    => Abstract(At())
          case "Location"    => Location(At())
          case "Nothing"     => Nothing(At())
          case "Time"        => Time(At())
          case "DateTime"    => DateTime(At())
          case "Duration"    => Duration(At())
          case "Current"     => Current(At())
          case "Length"      => Length(At())
          case "Luminosity"  => Luminosity(At())
          case "Mass"        => Mass(At())
          case "Mole"        => Mole(At())
          case "Temperature" => Temperature(At())
          case other =>
            ctx.err(s"unknown predefined type kind '$other'")
            Abstract(At())

      case DecimalDto(w, f)   => Decimal(At(), w.getOrElse(12L), f.getOrElse(2L))
      case CurrencyDto(c)     => Currency(At(), c.getOrElse("USD"))
      case RangeDto(min, max) => RangeType(At(), min.getOrElse(0L), max.getOrElse(100L))

      case PatternDto(ps) =>
        if ps.isEmpty then
          ctx.err("Pattern type requires at least one regular expression")
          Pattern(At(), Seq.empty)
        else Pattern(At(), ps.map(LiteralString(At(), _)))

      case EnumDto(es) =>
        if es.isEmpty then
          ctx.err("Enum type requires at least one value")
          Enumeration(At(), Contents.empty[Enumerator]())
        else
          val enumerators =
            es.map(e => Enumerator(At(), ident(e.name), e.value, Contents.empty[MetaData]()))
          Enumeration(At(), Contents[Enumerator](enumerators*))

      case AlternationDto(of) =>
        val aliases = of.map(t => AliasedTypeExpression(At(), "type", pathId(t)))
        Alternation(At(), Contents[AliasedTypeExpression](aliases*))

      case RecordDto(fields, methods) =>
        // A named Record becomes a proper RIDDL `record` (an aggregate tagged
        // RecordCase), not a bare aggregation, so that a `state ... of record X`
        // reference resolves (ResolutionPass.handleTypeResolution).
        AggregateUseCaseTypeExpression(
          At(),
          AggregateUseCase.RecordCase,
          contentsOf[AggregateContents](fields.map(buildField), methods.map(buildMethod))
        )

      case AliasDto(ref) => AliasedTypeExpression(At(), "type", pathId(ref))

      case URIDto(scheme) => URI(At(), scheme.map(LiteralString(At(), _)))

      case BlobDto(blobKind) =>
        val bk = blobKind match
          case None => BlobKind.Text
          case Some(s) =>
            scala.util.Try(BlobKind.valueOf(s)).toOption.getOrElse {
              ctx.err(s"unknown blob kind '$s'")
              BlobKind.Text
            }
        Blob(At(), bk)

      case ZonedDto(kind, zone) =>
        kind match
          case "ZonedDate"     => ZonedDate(At(), zone.map(LiteralString(At(), _)))
          case "ZonedDateTime" => ZonedDateTime(At(), zone.map(LiteralString(At(), _)))
          case other =>
            ctx.err(s"unknown zoned time kind '$other'")
            ZonedDateTime(At(), zone.map(LiteralString(At(), _)))

      case CollectionDto(kind, of) =>
        val inner = buildTypeExpr(of)
        kind match
          case "Sequence" => Sequence(At(), inner)
          case "Set"      => Set(At(), inner)
          case "Graph"    => Graph(At(), inner)
          case "Replica"  => Replica(At(), inner)
          case other =>
            ctx.err(s"unknown collection kind '$other'")
            Sequence(At(), inner)

      case MappingDto(from, to) => Mapping(At(), buildTypeExpr(from), buildTypeExpr(to))

      case TableDto(of, dimensions) => Table(At(), buildTypeExpr(of), dimensions)

      case EntityRefDto(entity) =>
        entity match
          case Some(e) => EntityReferenceTypeExpression(At(), pathId(e))
          case None =>
            ctx.err("EntityReference type requires an 'entity' path")
            EntityReferenceTypeExpression(At(), PathIdentifier.empty)

      case CardinalityDto(card, of, min, max) =>
        val inner = buildTypeExpr(of)
        card match
          case "optional"   => Optional(At(), inner)
          case "zeroOrMore" => ZeroOrMore(At(), inner)
          case "oneOrMore"  => OneOrMore(At(), inner)
          case "range"      => SpecificRange(At(), inner, min.getOrElse(0L), max.getOrElse(1L))
          case other =>
            ctx.err(s"unknown cardinality '$other' (expected optional|zeroOrMore|oneOrMore|range)")
            inner
    end match
  end buildTypeExpr

end JsonAstBuilder
