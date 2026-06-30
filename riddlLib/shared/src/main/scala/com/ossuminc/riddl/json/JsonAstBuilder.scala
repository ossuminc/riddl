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
    val root = Root(At(), Contents[RootContents](domains*))
    if ctx.errors.isEmpty then Right(root) else Left(ctx.errors.toList)
  end build

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
    val contexts = d.contexts.map(buildContext)
    Domain(
      At(),
      ident(d.name),
      contentsOf[DomainContents](authors, users, types, contexts),
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
    def agg(fields: Seq[FieldDto]): Option[Aggregation] =
      if fields.isEmpty then None
      else Some(Aggregation(At(), Contents[AggregateContents](fields.map(buildField)*)))
    val types = f.types.map(buildType)
    val statements = f.statements.map(buildStatement)
    val functions = f.functions.map(buildFunction)
    Function(
      At(),
      ident(f.name),
      agg(f.input),
      agg(f.output),
      contentsOf[FunctionContents](types, statements, functions),
      meta(f.brief)
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
