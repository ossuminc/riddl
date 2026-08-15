/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

/** The JSON wire schema for the RIDDL JSON input method.
  *
  * These DTO (data transfer object) case classes describe the JSON document an external producer
  * (e.g. an AI model) emits. They are decoupled from the RIDDL [[com.ossuminc.riddl.language.AST]]:
  * [[JsonAstBuilder]] maps a [[JsonModel.RootDto]] onto an `AST.Root`, applying RIDDL's required
  * defaults so the result is correct-by-construction for the supported subset.
  *
  * Serialization uses upickle, which is cross-compiled for JVM, JS, and Native, keeping the whole
  * path Native-safe (no I/O, no JVM-only dependency).
  *
  * Phase 1 subset: domains, contexts, entities, types, fields, messages
  * (command/event/query/result), state (record reference), handlers with on-clauses, invariants,
  * authors, and the common type expressions. Later phases extend the schema additively.
  */
object JsonModel:

  // ---------------------------------------------------------------------------
  // Type expressions (polymorphic — tagged by `kind`, or a `cardinality`
  // wrapper). Hand-written ReadWriter below; see JSON_INPUT.md for the schema.
  // ---------------------------------------------------------------------------

  sealed trait TypeExprDto

  /** `{ "kind": "String", "min"?: Int, "max"?: Int }` */
  case class StringDto(min: Option[Long] = None, max: Option[Long] = None) extends TypeExprDto

  /** `{ "kind": "Id", "entity": "<path>", "keyword"?: "entity" }` — entity path required by
    * builder. `keyword` is the AS-WRITTEN processor-kind keyword (`Id(entity Order)` ->
    * `"entity"`, `Id(Order)` -> absent); added 2026-08-13 alongside `AST.UniqueId.kindKeyword`.
    */
  case class IdDto(entity: Option[String] = None, keyword: Option[String] = None)
      extends TypeExprDto

  /** Argument-less predefined kinds: UUID, Boolean, Date, TimeStamp, Integer, Whole, Natural,
    * Number, Real.
    */
  case class PredefDto(kind: String) extends TypeExprDto

  /** `{ "kind": "Decimal", "whole"?: Int, "fractional"?: Int }` */
  case class DecimalDto(whole: Option[Long] = None, fractional: Option[Long] = None)
      extends TypeExprDto

  /** `{ "kind": "Currency", "country"?: String }` */
  case class CurrencyDto(country: Option[String] = None) extends TypeExprDto

  /** `{ "kind": "Range", "min"?: Int, "max"?: Int }` */
  case class RangeDto(min: Option[Long] = None, max: Option[Long] = None) extends TypeExprDto

  /** `{ "kind": "Pattern", "pattern": ["regex", ...] }` — builder requires >=1. */
  case class PatternDto(pattern: Seq[String] = Nil) extends TypeExprDto

  /** An enumerator: `"Red"` (name only) or `{ "name": "Red", "value": 0 }`. */
  case class EnumeratorDto(name: String, value: Option[Long] = None)

  /** `{ "kind": "Enum", "values": [...] }` and/or `{ ..., "enumerators": [...] }`; builder requires
    * >= 1 enumerator.
    */
  case class EnumDto(enumerators: Seq[EnumeratorDto] = Nil) extends TypeExprDto

  /** `{ "kind": "Alternation", "of": ["TypeA", "TypeB"] }` */
  case class AlternationDto(of: Seq[String] = Nil) extends TypeExprDto

  /** A method argument: `{ "name": "x", "type": <typeExpr> }` (Phase 3) */
  case class MethodArgDto(name: String, `type`: TypeExprDto)

  /** A record method: `{ "name": "total", "type": <typeExpr>, "args"?: [<arg>], "brief"?: ... }`.
    */
  case class MethodDto(
    name: String,
    `type`: TypeExprDto,
    args: Seq[MethodArgDto] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "kind": "Record", "fields": [ <field> ], "methods"?: [ <method> ], "comments"?: [ <comment>
    * ], "aggregate"?: "record" }` -> aggregate.
    *
    * `aggregate` names the aggregate's FLAVOUR, which is what distinguishes `type X is { … }` from
    * `record X is { … }` from `graph X is { … }`: it is `"aggregation"` for a bare `{…}` carrying
    * no keyword at all, and otherwise the RIDDL type keyword — `record`, `type`, `graph`, `table`,
    * `command`, `event`, `query` or `result`. `JsonifierPass` always writes it; when it is absent
    * (hand-authored JSON) the builder reads `record`, which is the long-standing behaviour and the
    * one that lets `state … of record X` resolve.
    */
  case class RecordDto(
    fields: Seq[FieldDto] = Nil,
    methods: Seq[MethodDto] = Nil,
    comments: Seq[CommentDto] = Nil,
    aggregate: Option[String] = None
  ) extends TypeExprDto

  /** `{ "kind": "Alias", "ref": "SomeDeclaredType" }` */
  case class AliasDto(ref: String) extends TypeExprDto

  /** `{ "kind": "URI", "scheme"?: "https" }` (Phase 2) */
  case class URIDto(scheme: Option[String] = None) extends TypeExprDto

  /** `{ "kind": "Blob", "blobKind"?: "JSON" }` (Phase 2; default Text) */
  case class BlobDto(blobKind: Option[String] = None) extends TypeExprDto

  /** `{ "kind": "ZonedDate"|"ZonedDateTime", "zone"?: "UTC" }` (Phase 2) */
  case class ZonedDto(kind: String, zone: Option[String] = None) extends TypeExprDto

  /** `{ "kind": "Sequence"|"Set"|"Graph"|"Replica", "of": <typeExpr> }` (Phase 2) */
  case class CollectionDto(kind: String, of: TypeExprDto) extends TypeExprDto

  /** `{ "kind": "Mapping", "from": <typeExpr>, "to": <typeExpr> }` (Phase 2) */
  case class MappingDto(from: TypeExprDto, to: TypeExprDto) extends TypeExprDto

  /** `{ "kind": "Table", "of": <typeExpr>, "dimensions": [Int, ...] }` (Phase 2) */
  case class TableDto(of: TypeExprDto, dimensions: Seq[Long] = Nil) extends TypeExprDto

  /** `{ "kind": "EntityReference", "entity": "<path>" }` (Phase 2; required path) */
  case class EntityRefDto(entity: Option[String] = None) extends TypeExprDto

  /** `{ "cardinality": "optional"|"zeroOrMore"|"oneOrMore"|"range", "of": <typeExpr>, "min"?: Int,
    * "max"?: Int }` — min/max only used by "range" (SpecificRange).
    */
  case class CardinalityDto(
    cardinality: String,
    of: TypeExprDto,
    min: Option[Long] = None,
    max: Option[Long] = None
  ) extends TypeExprDto

  // ---------------------------------------------------------------------------
  // Structural DTOs
  // ---------------------------------------------------------------------------

  /** Anything that can appear in a container's `contents`.
    *
    * RIDDL is fully reflective: a model written to JSON and read back must recover the EXACT AST,
    * and that includes the ORDER of definitions within their parent. The per-kind buckets this
    * schema started with (`domains`, `authors`, `comments`, …) cannot express order — reassembly
    * concatenates the buckets in a fixed sequence, so a comment written at the top of a file comes
    * back at the bottom. One ordered array of kind-tagged objects can.
    *
    * This is the same shape [[StatementDto]] already uses for a handler body, where statements and
    * the comments interleaved between them share one ordered list and round-trip correctly. See
    * `readContent`/`writeContent`, which mirror `readStatement`/`writeStatement`.
    */
  /** A UNION rather than a sealed trait, deliberately, for two reasons.
    *
    * It is how the AST itself models the same idea — `DomainContents`, `OccursInProcessor` and
    * friends are unions of the kinds a container admits.
    *
    * And upickle derives TAGGED codecs for members of a sealed hierarchy: making these DTOs extend
    * a sealed trait silently added a `$type` discriminator to every object in the schema, so every
    * hand-authored document stopped loading. A union carries no inheritance, so each DTO's derived
    * codec is untouched — while Scala 3 still checks the `writeContent` match for exhaustivity,
    * which is the whole reason not to use a bare `Any`.
    */
  type ContentDto = DomainDto | ModuleDto | ContextDto | EntityDto | TypeDefDto | MessageDto |
    StateDto | CorrelationDto | HandlerDto | OnClauseDto | FunctionDto | AdaptorDto | StreamletDto |
    ProjectorDto |
    RepositoryDto | SchemaDto | ConnectorDto | RelationshipDto | SagaDto | SagaStepDto | EpicDto |
    UseCaseDto | GroupDto | ContainedGroupDto | InputDto | OutputDto | AuthorDto | UserDto |
    InvariantDto | ConstantDto | CommentDto | VersionDto | CopyrightDto | PortletDto | FieldDto |
    MethodDto | TermDto | InteractionContentDto | IncludeContentDto | BASTImportContentDto |
    RequiresDto | ReturnsDto

  /** One entry of an ordered `contents` array: a child, and where it came from.
    *
    * `at` is `[offset, endOffset]` into whatever source [[LocationsDto]] names. It rides beside the
    * `$kind` tag rather than living on the ~36 [[ContentDto]] case classes, so carrying locations
    * costs those types nothing.
    */
  case class ContentEntry(content: ContentDto, at: Option[(Int, Int)] = None)

  /** How to read every `$at` in the document.
    *
    * The basis follows PROVENANCE. A model that came from RIDDL keeps its RIDDL offsets (`basis:
    * "origin"`), exactly as BAST does — BAST being an intermediary rather than a source is why it
    * may fabricate line numbers and this may not. A model authored directly as JSON uses offsets
    * into the JSON document itself (`basis: "document"`), which the reader HAS, so its line/col are
    * exact and a diagnostic can quote the line the author wrote.
    *
    * Absent entirely means no locations: every node gets `At.empty`, which is what documents
    * written before this keep doing.
    */
  case class LocationsDto(origin: String, basis: String = LocationBasis.Origin)

  object LocationBasis:
    /** Offsets index the file named by `LocationsDto.origin`. */
    val Origin = "origin"

    /** Offsets index THIS JSON document. */
    val Document = "document"
  end LocationBasis

  /** The `kind` tag a [[ContentDto]] is written under, and read back by.
    *
    * The tags are RIDDL keywords rather than DTO names, so a document reads like the language it
    * describes. Four kinds share [[MessageDto]] and are told apart by the tag alone, which is why
    * `MessageDto.usecase` exists — see its comment.
    */
  object ContentKind:
    val Domain = "domain"
    val Module = "module"
    val Context = "context"
    val Entity = "entity"
    val Type = "type"
    val Command = "command"
    val Event = "event"
    val Query = "query"
    val Result = "result"
    val State = "state"
    val Correlation = "correlation"
    val Handler = "handler"
    val OnClause = "onClause"
    val Function = "function"
    val Adaptor = "adaptor"
    val Streamlet = "streamlet"
    val Projector = "projector"
    val Repository = "repository"
    val Schema = "schema"
    val Connector = "connector"
    val Relationship = "relationship"
    val Saga = "saga"
    val SagaStep = "step"
    val Epic = "epic"
    val UseCase = "case"
    val Group = "group"
    val ContainedGroup = "containedGroup"
    val Input = "input"
    val Output = "output"
    val Author = "author"
    val User = "user"
    val Invariant = "invariant"
    val Constant = "constant"
    val Comment = "comment"
    val Version = "version"
    val Copyright = "copyright"
    val Inlet = "inlet"
    val Outlet = "outlet"
    val Field = "field"
    val Method = "method"
    val Term = "term"
    val Interaction = "interaction"
    val Include = "include"
    val BASTImport = "import"
    val Requires = "requires"
    val Returns = "returns"

    /** The four use cases that share [[MessageDto]]. */
    val messageKinds: Set[String] = Set(Command, Event, Query, Result)
  end ContentKind

  case class RootDto(
    /** How every `$at` in this document is to be read; absent means no locations at all. */
    locations: Option[LocationsDto] = None,
    domains: Seq[DomainDto] = Nil,
    modules: Seq[ModuleDto] = Nil,
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // `OccursInRoot` admits a top-level author, which the schema used to drop on the floor.
    authors: Seq[AuthorDto] = Nil,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** A Module is a FLAT collection of ANY top-level definition — no hierarchy is enforced at its
    * top level. The DTO therefore carries a group per definition kind, exactly the union
    * `AST.OccursInModule` expresses (S61-1; the first four fields predate the widening).
    */
  case class ModuleDto(
    name: String,
    brief: Option[String] = None,
    authors: Seq[AuthorDto] = Nil,
    domains: Seq[DomainDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    invariants: Seq[InvariantDto] = Nil,
    users: Seq[UserDto] = Nil,
    contexts: Seq[ContextDto] = Nil,
    entities: Seq[EntityDto] = Nil,
    adaptors: Seq[AdaptorDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    projectors: Seq[ProjectorDto] = Nil,
    repositories: Seq[RepositoryDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    sagas: Seq[SagaDto] = Nil,
    epics: Seq[EpicDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    modules: Seq[ModuleDto] = Nil,
    metadata: Option[MetaDto] = None,
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  case class DomainDto(
    name: String,
    brief: Option[String] = None,
    authors: Seq[AuthorDto] = Nil,
    users: Seq[UserDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    sagas: Seq[SagaDto] = Nil,
    epics: Seq[EpicDto] = Nil,
    domains: Seq[DomainDto] = Nil,
    contexts: Seq[ContextDto] = Nil,
    metadata: Option[MetaDto] = None,
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // `OccursInDomain` also admits messages, repositories and connectors: a domain may declare the
    // message vocabulary its contexts share, own a repository, and wire a cross-context connector.
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    repositories: Seq[RepositoryDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** `{ "name": "Shopper", "isA": "a person", "brief"?: ... }` (Phase 2) */
  case class UserDto(
    name: String,
    isA: String,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  case class AuthorDto(
    name: String,
    fullName: String,
    email: String,
    organization: Option[String] = None,
    title: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  case class TypeDefDto(
    name: String,
    typeExpression: TypeExprDto,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  case class ContextDto(
    name: String,
    brief: Option[String] = None,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    entities: Seq[EntityDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    adaptors: Seq[AdaptorDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    projectors: Seq[ProjectorDto] = Nil,
    repositories: Seq[RepositoryDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    sagas: Seq[SagaDto] = Nil,
    groups: Seq[GroupDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    metadata: Option[MetaDto] = None,
    // A Context is a Processor: it may carry an optional intention keyword prefix, an optional
    // ascribed shape, and inlet/outlet ports declared directly in its body.
    intention: Option[String] = None,
    shape: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // `OccursInProcessor` admits an invariant directly on the processor, not only on a state.
    invariants: Seq[InvariantDto] = Nil,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  case class MessageDto(
    name: String,
    brief: Option[String] = None,
    fields: Seq[FieldDto] = Nil,
    // A19: for a command/query, the optional message it yields (a command yields an event; a
    // query yields a result).
    yields: Option[MessageRefDto] = None,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** Which of the four message use cases this is, as a [[ContentKind]] tag.
      *
      * In the bucketed form the bucket said so (`commands`, `events`, …) and the DTO did not need
      * to. In the ordered `contents` array the `kind` tag is the only carrier, so it lives here.
      * `None` means the document did not say — which only happens for a bucketed document, where
      * the bucket supplies it, so the field is deliberately an Option rather than defaulting to
      * "command" and quietly mislabelling an event.
      */
    usecase: Option[String] = None
  )

  case class EntityDto(
    name: String,
    brief: Option[String] = None,
    // `state` (singular) is kept for back-compat; `states` (plural) models real
    // entity state machines — multiple named states, each with nested handlers.
    state: Option[StateDto] = None,
    states: Seq[StateDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    invariants: Seq[InvariantDto] = Nil,
    metadata: Option[MetaDto] = None,
    // A Processor may carry an optional ascribed shape and inlet/outlet ports.
    shape: Option[String] = None,
    // Semantic keywords written before `entity` (event-sourced, persistent, transient,
    // aggregate, consistent, available), in canonical order. Formerly options.
    intentions: Seq[String] = Nil,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // The unified processor model made an entity port-bearing, which also lets it own the
    // streamlets, connectors and relationships that wire those ports up.
    streamlets: Seq[StreamletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** `{ "name": "MaxItems", "type": <typeExpr>, "value": <value>, "brief"?: ... }` (Phase 2).
    *
    * `value` is a [[ValueDto]], not a bare string, because `AST.Constant.value` is
    * `ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue` -- a constant
    * can hold any of the four. `valueDtoRW` (below) already handles the polymorphism, so
    * `macroRW` picks it up with no further work.
    */
  case class ConstantDto(
    name: String,
    `type`: TypeExprDto,
    value: ValueDto,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  case class StateDto(
    name: String,
    recordType: String,
    handlers: Seq[HandlerDto] = Nil,
    invariants: Seq[InvariantDto] = Nil,
    brief: Option[String] = None,
    isInitial: Boolean = false,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** A70: a keyed accumulation of several events into one command, inside a projector.
    *
    * `keys` is a JSON ARRAY and its order is significant — §6.5 makes identity the full tuple and
    * forbids canonicalizing, so a consumer must not sort it. `timeout` is the duration as written
    * (`"30 days"`, or ISO-8601); it is mandatory in the language, hence not an Option here.
    *
    * `yieldsCommand` is a bare path (`Sales.RecordFulfillment`); the `command` kind is implied by
    * the field, as it is by the position in the grammar. It was `yieldsRecord` until 2026-08-12,
    * when a correlation's target became a command — the value's SHAPE never changed, only what it
    * truthfully names.
    */
  case class CorrelationDto(
    name: String,
    keys: Seq[String],
    yieldsCommand: String,
    timeout: String,
    timeoutStatements: Seq[StatementDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  case class HandlerDto(
    name: String,
    brief: Option[String] = None,
    onClauses: Seq[OnClauseDto] = Nil,
    isInitial: Boolean = false,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** `kind`: "message" | "init" | "other" | "term". For "message", `message` carries the message
    * ref + its kind. `statements` are tagged statement objects (or a bare string = prompt).
    * `binding` is A55's optional local name bound to the handled message (`on foo: command Foo`);
    * it is DEFAULTED so JSON written before A55 still reads.
    */
  /** An on-clause's `from [<name>:] <origin>`: which processor the message came from, and the
    * optional local name bound to it. Absent everywhere before this, so the builder hardcoded
    * `None` and every `on … from …` silently lost its origin.
    */
  case class OnFromDto(ref: RefDto, name: Option[String] = None)

  case class OnClauseDto(
    kind: String,
    message: Option[MessageRefDto] = None,
    statements: Seq[StatementDto] = Nil,
    // A55 for a message-like clause (the handled message's local name); A57 for `on other`, where
    // it names the ENVELOPE rather than a message. One field, because both are "the local name
    // this clause binds" and the `kind` already says which meaning applies.
    binding: Option[String] = None,
    // A57: `on other as x: <envelope>` -- the optional explicit envelope type. Only meaningful
    // alongside a binding on an `other` clause.
    envelope: Option[String] = None,
    from: Option[OnFromDto] = None,
    metadata: Option[MetaDto] = None,
    // An on-clause may be documented like any other definition (`on other { … } with { briefly … }`).
    brief: Option[String] = None,
    // Task 3: `on init`/`on term` parameter lists (`kind: "init"|"term"` only). Reuses the same
    // shape `MethodDto.args` already uses for a record method's arguments.
    parameters: Seq[MethodArgDto] = Nil
  )

  case class MessageRefDto(ref: String, kind: String)

  /** `condition` holds an opaque pseudo-code string; A28's structured BooleanExpression condition
    * is carried in `expression` (with `condition` empty). At most one is populated.
    */
  /** An invariant.
    *
    * `condition` carries the literal-string form; `expression` the structured boolean; `block` the
    * statements-plus-predicate form. Exactly one is populated.
    *
    * `requires` is not decoration — it decides WHERE the invariant applies (§15.2 of the
    * computational model), so a document that drops it describes a different model. `requiresKind`
    * discriminates `"state"` from `"type"`, which the ref string alone cannot: `state Open` and a
    * type named `Open` render differently but a bare path would not say which was meant.
    */
  case class InvariantDto(
    name: String,
    condition: String,
    brief: Option[String] = None,
    expression: Option[ValueDto] = None,
    metadata: Option[MetaDto] = None,
    requires: Option[String] = None,
    requiresKind: Option[String] = None,
    block: Option[InvariantBlockDto] = None
  )

  /** The block condition: pure statements then the boolean that is the predicate. */
  case class InvariantBlockDto(statements: Seq[StatementDto] = Nil, predicate: ValueDto)

  /** A53: a scope's version component — `{ "name": "Garibaldi" }` for the named form or `{ "name":
    * "4", "numeric": true }` for the numeric one. `name` always carries the RENDERED component;
    * `numeric` is the discriminator that says how it was written.
    */
  case class VersionDto(
    name: String,
    numeric: Boolean = false,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** A47: a scope's copyright notice — `{ "name": "C", "text": "\u00a9 2026 Ossum Inc." }`. `text`
    * is the notice VERBATIM and in its entirety; RIDDL never decomposes it.
    */
  case class CopyrightDto(
    name: String,
    text: String,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  case class FieldDto(
    name: String,
    `type`: TypeExprDto,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** A9: a Function/Saga `requires`/`returns` value — either a named type reference (`ref` =
    * "keyword path", e.g. "record Args", the preferred form) or a deprecated inline field list
    * (`fields`). Exactly one is populated.
    */
  case class ArgDto(ref: Option[String] = None, fields: Seq[FieldDto] = Nil)

  /** A function's or saga's `requires` clause as an ORDERED CONTENT ENTRY.
    *
    * `FunctionDto.input` / `SagaDto.input` carry the same value as a named field, and still do —
    * but a field has no position, and these clauses now sit in their container's contents where an
    * author may write a comment above, between or below them. Reconstructing from the fields alone
    * always yields "clauses first, comments after", which is a different document from the one that
    * was read. So the clause travels as a content entry like every other child, and the fields stay
    * as the deprecated bucketed form for documents written against the older schema.
    */
  case class RequiresDto(arg: ArgDto)

  /** A function's or saga's `returns` clause as an ordered content entry. See [[RequiresDto]]. */
  case class ReturnsDto(arg: ArgDto)

  /** A function: `input`/`output` are `requires`/`returns` args (a type ref or, deprecated, an
    * inline field list), `statements` is the body, `functions` are nested. (Phase 3)
    */
  case class FunctionDto(
    name: String,
    brief: Option[String] = None,
    input: Option[ArgDto] = None,
    output: Option[ArgDto] = None,
    types: Seq[TypeDefDto] = Nil,
    statements: Seq[StatementDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  // ---------------------------------------------------------------------------
  // Statements (Phase 3) — each kind is its own tagged object. A bare JSON
  // string is shorthand for a `prompt` statement.
  // ---------------------------------------------------------------------------

  sealed trait StatementDto

  /** `"text"` or `{ "kind": "prompt", "text": "..." }` */
  case class PromptStmtDto(text: String) extends StatementDto

  /** `{ "kind": "comment", "text": "...", "inline"?: true }`.
    *
    * `AST.Statements` is `Statement | Comment`, so a comment written between two statements — or
    * inside a `when`/`foreach` body or a saga step — is part of the statement list, not trivia
    * hanging off the enclosing definition. It needs an arm here or it has nowhere to go.
    */
  case class CommentStmtDto(text: String, inline: Boolean = false) extends StatementDto

  /** `{ "kind": "error", "message": "..." }` */
  case class ErrorStmtDto(message: String) extends StatementDto

  /** `{ "kind": "let", "name": "x", "type"?: "<typePath>", "expression": <value> }` */
  case class LetStmtDto(name: String, `type`: Option[String], expression: ValueDto)
      extends StatementDto

  /** `{ "kind": "code", "language": "scala", "body": "..." }` */
  case class CodeStmtDto(language: String, body: String) extends StatementDto

  /** `{ "kind": "require", "condition": "..." }`, `{ ..., "invariant": "<name>" }`, or A28's
    * structured `{ ..., "expression": <value> }`. At most one is populated.
    */
  /** `argument` is the `with <expr>` value handed to an invariant declaring `requires <type>` --
    * semantic, not decoration, so it round-trips like any other operand.
    */
  case class RequireStmtDto(
    condition: Option[String],
    invariant: Option[String],
    expression: Option[ValueDto] = None,
    argument: Option[ValueDto] = None
  ) extends StatementDto

  /** A54: a message operand — a bare message ref or an inline constructor value. */
  type MsgOperandDto = MessageRefDto | ConstructorValueDto

  /** `{ "kind": "set", "field"|"state": "<path>", "value": <value> }` */
  case class SetStmtDto(field: Option[String], state: Option[String], value: ValueDto)
      extends StatementDto

  /** `{ "kind": "send", "message": <msgRef|constructor>, "to": "<path>", "portlet":
    * "inlet"|"outlet" }`
    */
  case class SendStmtDto(message: MsgOperandDto, to: String, portlet: String) extends StatementDto

  /** `{ "kind": "morph", "entity": "<path>", "state": "<path>", "value": <msgRef|constructor> }` */
  case class MorphStmtDto(entity: String, state: String, value: MsgOperandDto) extends StatementDto

  /** `{ "kind": "become", "entity": "<path>", "handler": "<path>" }` */
  case class BecomeStmtDto(entity: String, handler: String) extends StatementDto

  /** `{ "kind": "tell", "message": <msgRef|constructor>, "to": "<path>", "processor":
    * "entity"|"context"|"projector"|"repository"|"adaptor", "by"?: "<field-name>" }`
    *
    * `by` is the optional disambiguator, needed only when the message carries more than one field
    * typed `Id(target)`. Absent everywhere before this, so older JSON still reads.
    */
  case class TellStmtDto(
    message: MsgOperandDto,
    to: String,
    processor: String,
    by: Option[String] = None
  ) extends StatementDto

  /** `{ "kind": "yield", "message": <msgRef|constructor> }` -- a command emitting its event. */
  case class YieldStmtDto(message: MsgOperandDto) extends StatementDto

  /** `{ "kind": "reply", "message": <msgRef|constructor> }` -- a query answering with its result.
    *
    * Until 2.0 `"kind": "reply"` was read as a LEGACY ALIAS for yield, because `reply` was a
    * deprecated synonym in the language. It is now its own statement, so the two kinds are
    * distinct on the wire as well as in the AST.
    */
  case class ReplyStmtDto(message: MsgOperandDto) extends StatementDto

  /** `{ "kind": "when", "condition"|"conditionIdentifier"|"expression": ..., "then": [<stmt>],
    * "else"?: [<stmt>] }`. A28's structured BooleanExpression condition is carried in
    * `expression`; at most one of the three condition fields is populated. Negation is NOT a
    * separate field -- it is a `NotDto`-wrapped value inside `expression` (not/! synonymy task 4,
    * 2026-08-15), the same as everywhere else a `NotExpression` appears. The DTO carried its own
    * `negated: Boolean` field until this task, mirroring the AST's now-deleted
    * `WhenStatement.negated`; it was always written/read as a hardcoded `false` (task 2's minimal
    * accommodation) since the real payload already round-trips through `expression`.
    */
  case class WhenStmtDto(
    condition: Option[String],
    conditionIdentifier: Option[String],
    thenStatements: Seq[StatementDto],
    elseStatements: Seq[StatementDto],
    expression: Option[ValueDto] = None
  ) extends StatementDto

  /** `{ "kind": "match", "subject": <value>, "cases": [<matchCase>], "default"?: [<stmt>] }` (A29:
    * the subject is a structured [[ValueDto]] — a value ref, a `get`, or a legacy literal).
    */
  case class MatchStmtDto(subject: ValueDto, cases: Seq[MatchCaseDto], default: Seq[StatementDto])
      extends StatementDto

  /** `{ "kind": "foreach", "element": "o", "field"|"local": "<path>", "do": [<stmt>] }` — exactly
    * one of `field` (a FieldRef collection) or `local` (a `let`-bound local) is present.
    */
  case class ForeachStmtDto(
    element: String,
    valueElement: Option[String],
    field: Option[String],
    local: Option[String],
    doStatements: Seq[StatementDto]
  ) extends StatementDto

  /** `{ "pattern": <matchPattern>, "guard"?: <value>, "statements": [<stmt>] }` (A29). */
  case class MatchCaseDto(
    pattern: MatchPatternDto,
    guard: Option[ValueDto],
    statements: Seq[StatementDto]
  )

  /** A29: the structured pattern of a [[MatchCaseDto]]. Serialized as an object with a `kind`
    * discriminator via readMatchPattern/writeMatchPattern.
    */
  sealed trait MatchPatternDto

  /** `{ "kind": "type", "path": "<path>", "keyword"?: "<kw>" }` — a type-case (A29). */
  case class TypePatternDto(path: String, keyword: Option[String]) extends MatchPatternDto

  /** `{ "kind": "comparison", "op": "=="|..., "comparand": <value> }` — subject <op> comparand
    * (A29).
    */
  case class ComparisonPatternDto(op: String, comparand: ValueDto) extends MatchPatternDto

  /** `{ "kind": "literal", "text": "..." }` — a legacy pseudo-code label pattern (A29). */
  case class LiteralPatternDto(text: String) extends MatchPatternDto

  // A54: value-expression DTOs. Serialized inline within put/return via readValue/writeValue.
  sealed trait ValueDto

  /** `{ "value": "literal", "text": "..." }` */
  case class LiteralValueDto(text: String) extends ValueDto

  /** `{ "value": "numeric", "text": "..." }` — a numeric literal (`5`, `1.50`, `2E+8`). `text` is
    * the literal AS WRITTEN, never a `ujson.Num`: a Double would turn `1.50` into `1.5` and drop
    * the precision of a large integer, exactly the byte-exactness `AST.NumericLiteral` exists to
    * avoid (see its doc comment). Deliberately its own DTO/tag rather than reusing
    * `LiteralValueDto` — that would round-trip the TEXT but silently rebuild a `LiteralString`
    * instead of a `NumericLiteral`, losing node identity.
    */
  case class NumericLiteralDto(text: String) extends ValueDto

  /** `{ "value": "constructor", "refKind": "command"|"event"|"query"|"result"|"record", "ref":
    * "<path>", "args": [<constructorArg>] }`
    */
  case class ConstructorValueDto(refKind: String, ref: String, args: Seq[ConstructorArgDto])
      extends ValueDto

  /** `{ "value": "call", "function": "<path>", "args": [<constructorArg>] }` — a call of a pure
    * function to obtain its result (A24).
    */
  case class CallValueDto(function: String, args: Seq[ConstructorArgDto]) extends ValueDto

  /** `{ "value": "ask", "query": "<path>", "processor": "<path>" }` -- the correlation `ask`
    * declares. No answer type is carried: it is the query's declared `replies result X`, so
    * storing it would be a second place for the same fact to go stale.
    */
  case class AskValueDto(query: String, processor: String, processorKind: String) extends ValueDto

  /** `{ "value": "valueRef", "path": "<path>" }` */
  case class ValueRefDto(path: String) extends ValueDto

  /** `{ "value": "constantRef", "path": "<path>" }` — a `constant <path>` comparison operand (A28).
    */
  case class ConstantRefDto(path: String) extends ValueDto

  /** `{ "value": "prompt", "prompt": "...", "type"?: <typeExpr> }` — an AI-computed value (A54).
    * `type` is the optional `as <type>` ascription (A20 typed holes); omitted when unascribed, so an
    * untyped prompt's JSON is unchanged.
    */
  case class PromptValueDto(prompt: String, typeEx: Option[TypeExprDto] = None) extends ValueDto

  /** `{ "value": "get", "source": "input"|"state", "keyword": "<kw>", "ref": "<path>" }` — the
    * `keyword` preserves the InputRef alias (input/form/…) for reflection fidelity; a StateRef has
    * no keyword so it is omitted.
    */
  case class GetValueDto(source: String, keyword: Option[String], ref: String) extends ValueDto

  /** `{ "value": "boolLiteral", "bool": true|false }` — a boolean literal (A28). */
  case class BooleanLiteralDto(bool: Boolean) extends ValueDto

  /** `{ "value": "comparison", "op": "=="|..., "left": <value>, "right": <value> }` (A28). */
  case class ComparisonDto(op: String, left: ValueDto, right: ValueDto) extends ValueDto

  /** `{ "value": "logical", "op": "and"|"or", "left": <value>, "right": <value> }` (A28). */
  case class LogicalDto(op: String, left: ValueDto, right: ValueDto) extends ValueDto

  /** `{ "value": "not", "expr": <value> }` — logical negation (A28). */
  case class NotDto(expr: ValueDto) extends ValueDto

  /** `{ "value": "invariantCondition", "invariant": "<path>", "argument"?: <value> }` — an
    * invariant named in a `when`/`match` condition. `argument` is the optional `with <expr>`.
    */
  case class InvariantConditionDto(invariant: String, argument: Option[ValueDto] = None)
      extends ValueDto

  /** `{ "value": "self", "field"?: "id"|"version" }` — the running processor instance, and
    * `self.<field>` on it. The type is synthesized (see `AST.SelfValue`), so nothing here names a
    * path.
    */
  case class SelfValueDto(field: Option[String] = None) extends ValueDto

  /** `{ "value": "initiate", "processor": "<path>", "processorKind": "<kind>", "args":
    * [<constructorArg>] }` — A70/instance-identity: bring an instance into being and yield its
    * identity. No answer type is carried: it is always the synthesized `Id(<processor>)`, so
    * storing it would be a second place for the same fact to go stale (mirrors `AskValueDto`).
    */
  case class InitiateValueDto(
    processor: String,
    processorKind: String,
    args: Seq[ConstructorArgDto]
  ) extends ValueDto

  /** `{ "name"?: "<field>", "value": <value> }` — a positional or named constructor argument. */
  case class ConstructorArgDto(name: Option[String], value: ValueDto)

  /** `{ "kind": "put", "value": <value>, "output": "<path>" }` (A45) */
  case class PutStmtDto(value: ValueDto, output: String) extends StatementDto

  /** `{ "kind": "return", "value": <value> }` (A57) */
  case class ReturnStmtDto(value: ValueDto) extends StatementDto

  /** `{ "kind": "terminate", "target": <value>, "args": [<constructorArg>] }` --
    * A70/instance-identity: end the instance denoted by `target`, invoking its `on term`.
    *
    * **The `processor`/`processorKind` pair was REPLACED by `target` on 2026-08-15**, when
    * `terminate` stopped naming a processor type and started naming an instance: `target` is a
    * value whose type must be `Id(entity E)`. This is NOT the same shape as [[InitiateValueDto]]
    * any more -- `initiate` still names a type, because the instance does not exist yet.
    *
    * A producer still emitting `processor`/`processorKind` gets them SILENTLY DROPPED and a
    * `target` of `null`: [[JsonModel]]'s readers never diff present keys against consumed keys
    * (filed in BACKLOG § 1 as a defect class in its own right), so there is no diagnostic. That
    * is precisely why this doc comment records the change rather than merely describing the
    * current shape.
    */
  case class TerminateStmtDto(
    target: ValueDto,
    args: Seq[ConstructorArgDto]
  ) extends StatementDto

  // ---------------------------------------------------------------------------
  // Streaming & integration (Phase 4)
  // ---------------------------------------------------------------------------

  /** `{ "name": "A", "direction": "inbound"|"outbound", "context": "<path>", ... }` */
  case class AdaptorDto(
    name: String,
    direction: String,
    context: String,
    brief: Option[String] = None,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    // A Processor may carry an optional ascribed shape and inlet/outlet ports.
    shape: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    // A47: every Processor is a version and copyright scope.
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // The rest of `OccursInProcessor`, which every processor shares.
    invariants: Seq[InvariantDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** An inlet or outlet: `{ "name": "in", "type": "<typePath>", "brief"?: ... }`
    *
    * `direction` plays the same role for a portlet that `MessageDto.usecase` plays for a message:
    * in the bucketed form `inlets`/`outlets` said which this was, and in the ordered `contents`
    * array the `kind` tag is the only carrier. `None` means a bucketed document supplied it.
    */
  case class PortletDto(
    name: String,
    `type`: String,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None,
    direction: Option[String] = None,
    /** The TypeRef's KEYWORD (`command`, `event`, `record`, …). `inlet in is command Go` is not the
      * same source as `inlet in is type Go`, and dropping it rewrote every ported model's message
      * references as plain type references. Follows the `PutOutDto.keyword` precedent; absent means
      * the plain `type`.
      */
    keyword: Option[String] = None
  )

  /** `{ "name": "C", "from": "<outletPath>", "to": "<inletPath>", "brief"?: ... }` */
  case class ConnectorDto(
    name: String,
    from: String,
    to: String,
    /** Keywords written BEFORE `connector`: `persistent`, and one of `at-least-once` /
      * `at-most-once`. Absent delivery means at-least-once (Computational Model §25.7).
      */
    intentions: Seq[String] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "S", "shape"?: "source"|"sink"|"flow"|"merge"|"split"|"router"|"void", ... }`.
    * `shape` is the OPTIONAL author-ascribed shape (absent = derived from arity).
    */
  case class StreamletDto(
    name: String,
    shape: Option[String] = None,
    brief: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    // A47: every Processor is a version and copyright scope.
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // The rest of `OccursInProcessor`, which every processor shares.
    constants: Seq[ConstantDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    invariants: Seq[InvariantDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** `{ "name": "R", "withProcessor": "<path>", "processor": "entity"|..., "cardinality":
    * "1:1"|..., "label"?: "...", "brief"?: ... }`
    */
  case class RelationshipDto(
    name: String,
    withProcessor: String,
    processor: String,
    cardinality: String,
    label: Option[String] = None,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "P", "repository"?: "<path>", ... }` */
  case class ProjectorDto(
    name: String,
    brief: Option[String] = None,
    repository: Option[String] = None,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    // A Processor may carry an optional ascribed shape and inlet/outlet ports.
    shape: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    // A47: every Processor is a version and copyright scope.
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // The rest of `OccursInProcessor`, which every processor shares.
    invariants: Seq[InvariantDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** A repository schema: `{ "name": "S", "kind"?: "Relational"|..., "data"?: {field->typePath},
    * "links"?: {name->[fieldA,fieldB]}, "indices"?: [field], "brief"?: ... }`
    */
  case class SchemaDto(
    name: String,
    kind: Option[String] = None,
    data: Map[String, String] = Map.empty,
    links: Map[String, Seq[String]] = Map.empty,
    indices: Seq[String] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "Repo", "schema"?: <schema>, ... }` */
  case class RepositoryDto(
    name: String,
    brief: Option[String] = None,
    schema: Option[SchemaDto] = None,
    types: Seq[TypeDefDto] = Nil,
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    // A Processor may carry an optional ascribed shape and inlet/outlet ports.
    shape: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    // A47: every Processor is a version and copyright scope.
    version: Option[VersionDto] = None,
    copyright: Option[CopyrightDto] = None,
    // `schema` (singular) is kept for back-compat; a repository may declare several, so `schemas`
    // (plural) is the one that models it — the same shape `state`/`states` takes on an entity.
    schemas: Seq[SchemaDto] = Nil,
    // The rest of `OccursInProcessor`, which every processor shares.
    constants: Seq[ConstantDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    invariants: Seq[InvariantDto] = Nil,
    streamlets: Seq[StreamletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    relationships: Seq[RelationshipDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  // ---------------------------------------------------------------------------
  // Sagas (Phase 5)
  // ---------------------------------------------------------------------------

  /** `{ "name": "Reserve", "do": [<stmt>], "undo": [<stmt>], "brief"?: ... }` */
  case class SagaStepDto(
    name: String,
    `do`: Seq[StatementDto] = Nil,
    undo: Seq[StatementDto] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "Booking", "input"?: [<field>], "output"?: [<field>], "types"?: [...], "steps":
    * [<sagaStep>], "brief"?: ... }`
    */
  case class SagaDto(
    name: String,
    brief: Option[String] = None,
    input: Option[ArgDto] = None,
    output: Option[ArgDto] = None,
    types: Seq[TypeDefDto] = Nil,
    steps: Seq[SagaStepDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  // ---------------------------------------------------------------------------
  // Epics, use cases, interactions (Phase 7)
  // ---------------------------------------------------------------------------

  /** A generic definition reference: `{ "kind": "user"|"entity"|"context"|"group"|"output"|"input"|
    * "adaptor"|"projector", "path": "<path>" }`
    */
  /** A reference in an interaction. `keyword` preserves the ALIAS the source used — a group may be
    * written `button`, `page` or `form`, and rebuilding it as the bare `group`/`input`/`output`
    * rewrote the model's wording. Absent means the canonical keyword for that kind.
    */
  case class RefDto(kind: String, path: String, keyword: Option[String] = None)

  /** `{ "user": "<userPath>", "capability": "...", "benefit": "..." }` */
  case class UserStoryDto(user: String, capability: String, benefit: String)

  /** An interaction step. Tagged by `kind`; containers nest `interactions`. */
  sealed trait InteractionDto
  case class VagueIxnDto(from: String, relationship: String, to: String) extends InteractionDto
  case class SendMessageIxnDto(from: RefDto, message: MessageRefDto, to: String, processor: String)
      extends InteractionDto
  case class ArbitraryIxnDto(from: RefDto, relationship: String, to: RefDto) extends InteractionDto
  case class SelfIxnDto(from: RefDto, relationship: String) extends InteractionDto

  /** `keyword` preserves the group/input/output ALIAS the source used (`page`, `button`, `form`,
    * …); rebuilding it as the bare canonical keyword rewrote the model's wording.
    */
  case class FocusOnGroupIxnDto(user: String, group: String, keyword: Option[String] = None)
      extends InteractionDto
  case class DirectToURLIxnDto(user: String, url: String) extends InteractionDto
  case class ShowOutputIxnDto(
    output: String,
    relationship: String,
    user: String,
    keyword: Option[String] = None
  ) extends InteractionDto
  case class SelectInputIxnDto(user: String, input: String, keyword: Option[String] = None)
      extends InteractionDto
  case class TakeInputIxnDto(user: String, input: String, keyword: Option[String] = None)
      extends InteractionDto
  case class RefusalIxnDto(from: RefDto, user: String, reason: String) extends InteractionDto
  case class ParallelIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto
  case class SequentialIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto
  case class OptionalIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto

  /** `{ "name": "Pay", "userStory": <userStory>, "interactions": [<interaction>], "brief"?: ... }`
    */
  /** An interaction as an entry of a use case's ordered `contents`.
    *
    * `UseCaseContents` is `Interaction | Comment`, so the steps and the comments between them share
    * one ordered list in the AST. Carrying the steps in a separate `interactions` array meant the
    * two were concatenated rather than merged on the way back, and a comment written between two
    * steps moved to the front.
    */
  case class InteractionContentDto(interaction: InteractionDto)

  /** An `include "file"` wrapper, carrying its ALREADY-LOADED contents nested inside it.
    *
    * Nesting the contents rather than the file reference alone is what keeps the builder no-I/O and
    * so Native-safe: read-back reconstructs the node from the document alone and never touches the
    * filesystem. Before this the wrapper was dropped and its children inlined into the parent, so a
    * model lost the fact that it was split across files at all.
    */
  case class IncludeContentDto(origin: String, contents: Seq[ContentEntry] = Nil)

  /** An `import … from "file.bast"` wrapper, contents nested for the same reason as
    * [[IncludeContentDto]].
    */
  case class BASTImportContentDto(
    path: String,
    importKind: Option[String] = None,
    selector: Option[String] = None,
    alias: Option[String] = None,
    contents: Seq[ContentEntry] = Nil
  )

  case class UseCaseDto(
    name: String,
    userStory: UserStoryDto,
    interactions: Seq[InteractionDto] = Nil,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  /** `{ "name": "Checkout", "userStory": <userStory>, "shownBy"?: [url], "types"?: [...],
    * "useCases": [<useCase>], "brief"?: ... }`
    */
  case class EpicDto(
    name: String,
    userStory: UserStoryDto,
    brief: Option[String] = None,
    shownBy: Seq[String] = Nil,
    types: Seq[TypeDefDto] = Nil,
    useCases: Seq[UseCaseDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  // ---------------------------------------------------------------------------
  // UI groups (Phase 8)
  // ---------------------------------------------------------------------------

  /** What an output emits: `{ "kind": "type"|"constant"|"literal", "value": "<path or text>",
    * "keyword"?: "record" }` (keyword only for the "type" kind; default "type").
    */
  case class PutOutDto(kind: String, value: String, keyword: Option[String] = None)

  /** `{ "name": "Form", "nounAlias"?: "input", "verbAlias"?: "acquires", "takeIn": "<typePath>",
    * "inputs"?: [<input>], "brief"?: ... }`
    */
  case class InputDto(
    name: String,
    takeIn: String,
    /** The TypeRef keyword of `takeIn` — `acquires command X` rather than `acquires type X`. */
    keyword: Option[String] = None,
    nounAlias: Option[String] = None,
    verbAlias: Option[String] = None,
    brief: Option[String] = None,
    inputs: Seq[InputDto] = Nil,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "Page", "nounAlias"?: "output", "verbAlias"?: "displays", "putOut": <putOut>,
    * "outputs"?: [<output>], "brief"?: ... }`
    */
  case class OutputDto(
    name: String,
    putOut: PutOutDto,
    nounAlias: Option[String] = None,
    verbAlias: Option[String] = None,
    brief: Option[String] = None,
    outputs: Seq[OutputDto] = Nil,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "Sub", "group": "<groupPath>", "brief"?: ... }` */
  case class ContainedGroupDto(
    name: String,
    group: String,
    brief: Option[String] = None,
    metadata: Option[MetaDto] = None
  )

  /** `{ "name": "Main", "alias"?: "group", "groups"?: [...], "containedGroups"?: [...], "inputs"?:
    * [...], "outputs"?: [...], "brief"?: ... }`
    */
  case class GroupDto(
    name: String,
    alias: Option[String] = None,
    brief: Option[String] = None,
    groups: Seq[GroupDto] = Nil,
    containedGroups: Seq[ContainedGroupDto] = Nil,
    inputs: Seq[InputDto] = Nil,
    outputs: Seq[OutputDto] = Nil,
    metadata: Option[MetaDto] = None,
    comments: Seq[CommentDto] = Nil,
    /** This container's children, in SOURCE ORDER — the canonical form. The per-kind buckets above
      * are the deprecated form, kept readable for documents written against the older schema; they
      * cannot express order. See [[ContentDto]].
      */
    contents: Seq[ContentEntry] = Nil
  )

  // ---------------------------------------------------------------------------
  // Metadata (Phase 9) — richer than `brief`, on the primary containers.
  // ---------------------------------------------------------------------------

  /** `{ "name": "SKU", "definition": ["a stock keeping unit"] }` (glossary term) */
  case class TermDto(name: String, definition: Seq[String] = Nil)

  /** A comment that lives in a definition's CONTENTS rather than its metadata: `{ "text": "...",
    * "inline"?: true }`. `inline` distinguishes a `/* ... */` comment from a `//` one; an inline
    * comment's lines are joined with newlines in `text`.
    *
    * Comments group with their container's other children, so their position relative to the
    * definitions around them is not preserved — the schema groups every child by kind, so that
    * ordering is already gone for definitions too.
    */
  case class CommentDto(text: String, inline: Boolean = false)

  /** `{ "name": "microservice", "args": [] }` (an option value) */
  case class OptionDto(name: String, args: Seq[String] = Nil)

  /** A42: `{ "fileKey": "abc123", "nodeId": "1:23" }` — a structured Figma frame reference. */
  case class FigmaRefDto(fileKey: String, nodeId: String)

  /** `{ "name": "diagram", "mimeType": "image/svg", "value": "<text or path>", "inFile"?: false }`.
    * `inFile: true` -> a FileAttachment (value is a path); otherwise a StringAttachment.
    */
  case class AttachmentDto(name: String, mimeType: String, value: String, inFile: Boolean = false)

  /** Rich metadata shared by the primary containers (domain/context/entity/type). `brief` remains a
    * convenient top-level shorthand alongside this.
    */
  /** One entry of a `with { … }` block, in source order.
    *
    * `payload` is the entry's own shape, kind-tagged exactly as a [[ContentDto]] is: metadata had
    * the same defect the container buckets had, one level down — bucketing description, terms,
    * options, authors, attachments and comments separately means `option kind("device")` written
    * before `briefly "…"` comes back after it.
    */
  case class MetaItemDto(
    kind: String,
    lines: Seq[String] = Nil,
    name: Option[String] = None,
    definition: Seq[String] = Nil,
    args: Seq[String] = Nil,
    path: Option[String] = None,
    mimeType: Option[String] = None,
    value: Option[String] = None,
    inFile: Boolean = false,
    fileKey: Option[String] = None,
    nodeId: Option[String] = None,
    inline: Boolean = false
  )

  /** The `kind` values a [[MetaItemDto]] takes. */
  object MetaKind:
    val Description = "described"
    val UrlDescription = "describedAt"
    val Term = "term"
    val Option_ = "option"
    val AuthorRef = "byAuthor"
    val Attachment = "attachment"
    val Comment = "comment"
    val FigmaRef = "figma"
    val Brief = "briefly"
    val UlidAttachment = "ulid"
  end MetaKind

  case class MetaDto(
    description: Seq[String] = Nil,
    terms: Seq[TermDto] = Nil,
    options: Seq[OptionDto] = Nil,
    byAuthors: Seq[String] = Nil,
    attachments: Seq[AttachmentDto] = Nil,
    comments: Seq[String] = Nil,
    figmaRefs: Seq[FigmaRefDto] = Nil,
    // A `described at <url>` description, which points at prose kept outside the model.
    urlDescription: Option[String] = None,
    /** The `with { … }` entries in SOURCE ORDER — the canonical form. The buckets above are the
      * deprecated form, kept readable; they cannot express order.
      */
    items: Seq[MetaItemDto] = Nil
  )

  // ---------------------------------------------------------------------------
  // upickle wiring
  // ---------------------------------------------------------------------------

  /** Custom pickler that encodes `Option[T]` as null-or-value (the upickle default encodes Option
    * as a 0/1-element JSON array, which would force callers to write `"brief": ["text"]`). Absent
    * keys still fall back to the DTO's default argument.
    */
  object Pickle extends upickle.AttributeTagged:
    override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
      summon[Writer[T]].comap[Option[T]] {
        case Some(x) => x
        case None    => null.asInstanceOf[T]
      }

    override implicit def OptionReader[T: Reader]: Reader[Option[T]] =
      new Reader.Delegate[Any, Option[T]](summon[Reader[T]].map(Some(_))):
        override def visitNull(index: Int): Option[T] = None
  end Pickle

  import Pickle.{ReadWriter, macroRW, readwriter, read => readJson, writeJs}

  /** ujson <-> TypeExprDto. Hand-written so the dual discriminator (`cardinality` wrapper vs `kind`
    * tag) and the defaults are explicit.
    */
  private def readTypeExpr(v: ujson.Value): TypeExprDto =
    val m = v.obj
    if m.contains("cardinality") then
      // Tolerate inline cardinality (e.g. `{"kind":"Date","cardinality":"optional"}`)
      // by wrapping the rest of the object as the inner type expression when `of`
      // is absent.
      val inner =
        if m.contains("of") then readTypeExpr(m("of"))
        else readTypeExpr(ujson.Obj.from(m.iterator.filter(_._1 != "cardinality")))
      CardinalityDto(
        m("cardinality").str,
        inner,
        m.get("min").map(_.num.toLong),
        m.get("max").map(_.num.toLong)
      )
    else
      m("kind").str match
        case "String" => StringDto(m.get("min").map(_.num.toLong), m.get("max").map(_.num.toLong))
        case "Id" => IdDto(m.get("entity").map(_.str), m.get("keyword").map(_.str))
        // Argument-less predefined kinds (Phase 1 + Phase 2)
        case k @ ("UUID" | "Boolean" | "Date" | "TimeStamp" | "Integer" | "Whole" | "Natural" |
            "Number" | "Real" | "UserId" | "Anything" | "Abstract" | "Location" | "Nothing" |
            "Time" | "DateTime" | "Duration" | "Current" | "Length" | "Luminosity" | "Mass" |
            "Mole" | "Temperature") =>
          PredefDto(k)
        case "Decimal" =>
          DecimalDto(m.get("whole").map(_.num.toLong), m.get("fractional").map(_.num.toLong))
        case "Currency" => CurrencyDto(m.get("country").map(_.str))
        case "Range"    => RangeDto(m.get("min").map(_.num.toLong), m.get("max").map(_.num.toLong))
        case "Pattern"  => PatternDto(m.get("pattern").map(_.arr.map(_.str).toSeq).getOrElse(Nil))
        case "Enum" =>
          val fromValues =
            m.get("values").map(_.arr.map(x => EnumeratorDto(x.str)).toSeq).getOrElse(Nil)
          val fromEnums = m
            .get("enumerators")
            .map(
              _.arr
                .map(j => EnumeratorDto(j.obj("name").str, j.obj.get("value").map(_.num.toLong)))
                .toSeq
            )
            .getOrElse(Nil)
          EnumDto(fromValues ++ fromEnums)
        case "Alternation" => AlternationDto(m.get("of").map(_.arr.map(_.str).toSeq).getOrElse(Nil))
        case "Record" =>
          val fields =
            m.get("fields").map(_.arr.map(j => readJson[FieldDto](j)).toSeq).getOrElse(Nil)
          val methods = m.get("methods").map(_.arr.map(readMethod).toSeq).getOrElse(Nil)
          val comments =
            m.get("comments").map(_.arr.map(j => readJson[CommentDto](j)).toSeq).getOrElse(Nil)
          RecordDto(fields, methods, comments, m.get("aggregate").map(_.str))
        case "Alias"                             => AliasDto(m("ref").str)
        case "URI" | "URL"                       => URIDto(m.get("scheme").map(_.str))
        case "Blob"                              => BlobDto(m.get("blobKind").map(_.str))
        case k @ ("ZonedDate" | "ZonedDateTime") => ZonedDto(k, m.get("zone").map(_.str))
        case k @ ("Sequence" | "Set" | "Graph" | "Replica") =>
          CollectionDto(k, readTypeExpr(m("of")))
        case "Mapping" => MappingDto(readTypeExpr(m("from")), readTypeExpr(m("to")))
        case "Table" =>
          TableDto(
            readTypeExpr(m("of")),
            m.get("dimensions").map(_.arr.map(_.num.toLong).toSeq).getOrElse(Nil)
          )
        case "EntityReference" => EntityRefDto(m.get("entity").map(_.str))
        // Tolerate an unknown `kind` as a reference to a declared type of that
        // name (the most natural AI/human mistake, and unambiguous). Undefined
        // names then surface as normal ResolutionPass errors, not exceptions.
        case other => AliasDto(other)
    end if
  end readTypeExpr

  private def readMethod(j: ujson.Value): MethodDto =
    val o = j.obj
    val args = o
      .get("args")
      .map(_.arr.map(a => MethodArgDto(a.obj("name").str, readTypeExpr(a.obj("type")))).toSeq)
      .getOrElse(Nil)
    MethodDto(o("name").str, readTypeExpr(o("type")), args, o.get("brief").map(_.str))

  private def writeMethod(mth: MethodDto): ujson.Value =
    ujson.Obj.from(
      Seq[(String, ujson.Value)]("name" -> ujson.Str(mth.name), "type" -> writeTypeExpr(mth.`type`))
        ++ (if mth.args.nonEmpty then
              Seq[(String, ujson.Value)](
                "args" -> ujson.Arr.from(
                  mth.args.map(a =>
                    ujson.Obj("name" -> ujson.Str(a.name), "type" -> writeTypeExpr(a.`type`))
                  )
                )
              )
            else Nil)
        ++ mth.brief.map(b => "brief" -> (ujson.Str(b): ujson.Value))
    )

  private def writeTypeExpr(dto: TypeExprDto): ujson.Value =
    dto match
      case StringDto(min, max) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("String"))
            ++ min.map(x => "min" -> (ujson.Num(x.toDouble): ujson.Value))
            ++ max.map(x => "max" -> (ujson.Num(x.toDouble): ujson.Value))
        )
      case IdDto(entity, keyword) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Id"))
            ++ entity.map(e => "entity" -> (ujson.Str(e): ujson.Value))
            ++ keyword.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case PredefDto(kind) => ujson.Obj("kind" -> ujson.Str(kind))
      case DecimalDto(w, f) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Decimal"))
            ++ w.map(x => "whole" -> (ujson.Num(x.toDouble): ujson.Value))
            ++ f.map(x => "fractional" -> (ujson.Num(x.toDouble): ujson.Value))
        )
      case CurrencyDto(c) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Currency"))
            ++ c.map(x => "country" -> (ujson.Str(x): ujson.Value))
        )
      case RangeDto(min, max) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Range"))
            ++ min.map(x => "min" -> (ujson.Num(x.toDouble): ujson.Value))
            ++ max.map(x => "max" -> (ujson.Num(x.toDouble): ujson.Value))
        )
      case PatternDto(ps) =>
        ujson.Obj("kind" -> ujson.Str("Pattern"), "pattern" -> ujson.Arr.from(ps.map(ujson.Str(_))))
      case EnumDto(enumerators) =>
        ujson.Obj(
          "kind" -> ujson.Str("Enum"),
          "enumerators" -> ujson.Arr.from(enumerators.map { e =>
            ujson.Obj.from(
              Seq[(String, ujson.Value)]("name" -> ujson.Str(e.name))
                ++ e.value.map(x => "value" -> (ujson.Num(x.toDouble): ujson.Value))
            )
          })
        )
      case AlternationDto(of) =>
        ujson.Obj("kind" -> ujson.Str("Alternation"), "of" -> ujson.Arr.from(of.map(ujson.Str(_))))
      case RecordDto(fields, methods, comments, aggregate) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("Record"),
            "fields" -> ujson.Arr.from(fields.map(f => writeJs(f)))
          ) ++ (if methods.nonEmpty then
                  Seq[(String, ujson.Value)]("methods" -> ujson.Arr.from(methods.map(writeMethod)))
                else Nil)
            ++ (if comments.nonEmpty then
                  Seq[(String, ujson.Value)](
                    "comments" -> ujson.Arr.from(comments.map(c => writeJs(c)))
                  )
                else Nil)
            ++ aggregate.map(a => "aggregate" -> (ujson.Str(a): ujson.Value))
        )
      case AliasDto(ref) => ujson.Obj("kind" -> ujson.Str("Alias"), "ref" -> ujson.Str(ref))
      case URIDto(scheme) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("URI"))
            ++ scheme.map(s => "scheme" -> (ujson.Str(s): ujson.Value))
        )
      case BlobDto(blobKind) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Blob"))
            ++ blobKind.map(s => "blobKind" -> (ujson.Str(s): ujson.Value))
        )
      case ZonedDto(kind, zone) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str(kind))
            ++ zone.map(s => "zone" -> (ujson.Str(s): ujson.Value))
        )
      case CollectionDto(kind, of) =>
        ujson.Obj("kind" -> ujson.Str(kind), "of" -> writeTypeExpr(of))
      case MappingDto(from, to) =>
        ujson.Obj(
          "kind" -> ujson.Str("Mapping"),
          "from" -> writeTypeExpr(from),
          "to" -> writeTypeExpr(to)
        )
      case TableDto(of, dimensions) =>
        ujson.Obj(
          "kind" -> ujson.Str("Table"),
          "of" -> writeTypeExpr(of),
          "dimensions" -> ujson.Arr.from(dimensions.map(d => ujson.Num(d.toDouble)))
        )
      case EntityRefDto(entity) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("EntityReference"))
            ++ entity.map(e => "entity" -> (ujson.Str(e): ujson.Value))
        )
      case CardinalityDto(card, of, min, max) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("cardinality" -> ujson.Str(card), "of" -> writeTypeExpr(of))
            ++ min.map(x => "min" -> (ujson.Num(x.toDouble): ujson.Value))
            ++ max.map(x => "max" -> (ujson.Num(x.toDouble): ujson.Value))
        )
  end writeTypeExpr

  // ujson <-> StatementDto. A bare string is read as a `prompt` statement.

  private def msgRef(v: ujson.Value): MessageRefDto =
    MessageRefDto(v.obj("ref").str, v.obj("kind").str)
  private def msgRefJs(mr: MessageRefDto): ujson.Value =
    ujson.Obj("ref" -> ujson.Str(mr.ref), "kind" -> ujson.Str(mr.kind))
  private def readStmts(o: Option[ujson.Value]): Seq[StatementDto] =
    o.map(_.arr.map(readStatement).toSeq).getOrElse(Nil)

  // A29: ujson <-> MatchPatternDto.
  private def readMatchPattern(v: ujson.Value): MatchPatternDto =
    val m = v.obj
    m("kind").str match
      case "type"       => TypePatternDto(m("path").str, m.get("keyword").map(_.str))
      case "comparison" => ComparisonPatternDto(m("op").str, readValue(m("comparand")))
      case "literal"    => LiteralPatternDto(m("text").str)
      case other => throw new IllegalArgumentException(s"Unknown match pattern kind: '$other'")
  end readMatchPattern

  private def writeMatchPattern(dto: MatchPatternDto): ujson.Value =
    dto match
      case TypePatternDto(path, keyword) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("type"), "path" -> ujson.Str(path))
            ++ keyword.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case ComparisonPatternDto(op, comparand) =>
        ujson.Obj(
          "kind" -> ujson.Str("comparison"),
          "op" -> ujson.Str(op),
          "comparand" -> writeValue(comparand)
        )
      case LiteralPatternDto(text) =>
        ujson.Obj("kind" -> ujson.Str("literal"), "text" -> ujson.Str(text))
  end writeMatchPattern

  // A54: ujson <-> ValueDto.
  private def readValue(v: ujson.Value): ValueDto =
    v match
      case ujson.Str(s) => LiteralValueDto(s) // backward compat: a bare string is a literal value
      case _            => readValueObj(v)

  private def readValueObj(v: ujson.Value): ValueDto =
    val m = v.obj
    m("value").str match
      case "literal"     => LiteralValueDto(m("text").str)
      case "numeric"     => NumericLiteralDto(m("text").str)
      case "prompt"      => PromptValueDto(m("prompt").str, m.get("type").map(readTypeExpr))
      case "valueRef"    => ValueRefDto(m("path").str)
      case "constantRef" => ConstantRefDto(m("path").str)
      case "get"         => GetValueDto(m("source").str, m.get("keyword").map(_.str), m("ref").str)
      case "boolLiteral" => BooleanLiteralDto(m("bool").bool)
      case "comparison"  => ComparisonDto(m("op").str, readValue(m("left")), readValue(m("right")))
      case "logical"     => LogicalDto(m("op").str, readValue(m("left")), readValue(m("right")))
      case "not"         => NotDto(readValue(m("expr")))
      case "invariantCondition" =>
        InvariantConditionDto(m("invariant").str, m.get("argument").map(readValue))
      case "constructor" =>
        val args = m
          .get("args")
          .map(
            _.arr
              .map(a => ConstructorArgDto(a.obj.get("name").map(_.str), readValue(a.obj("value"))))
              .toSeq
          )
          .getOrElse(Nil)
        ConstructorValueDto(m("refKind").str, m("ref").str, args)
      case "call" =>
        val args = m
          .get("args")
          .map(
            _.arr
              .map(a => ConstructorArgDto(a.obj.get("name").map(_.str), readValue(a.obj("value"))))
              .toSeq
          )
          .getOrElse(Nil)
        CallValueDto(m("function").str, args)
      case "ask" =>
        AskValueDto(m("query").str, m("processor").str, m("processorKind").str)
      case "self" => SelfValueDto(m.get("field").map(_.str))
      case "initiate" =>
        val args = m
          .get("args")
          .map(
            _.arr
              .map(a => ConstructorArgDto(a.obj.get("name").map(_.str), readValue(a.obj("value"))))
              .toSeq
          )
          .getOrElse(Nil)
        InitiateValueDto(m("processor").str, m("processorKind").str, args)
      case other => throw new IllegalArgumentException(s"Unknown value kind: '$other'")
  end readValueObj

  private def writeValue(dto: ValueDto): ujson.Value =
    dto match
      case LiteralValueDto(text) =>
        ujson.Obj("value" -> ujson.Str("literal"), "text" -> ujson.Str(text))
      case NumericLiteralDto(text) =>
        // ALWAYS a JSON string, never ujson.Num -- a Double would turn 1.50 into 1.5. See the
        // DTO's doc comment.
        ujson.Obj("value" -> ujson.Str("numeric"), "text" -> ujson.Str(text))
      case PromptValueDto(prompt, typeEx) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("value" -> ujson.Str("prompt"), "prompt" -> ujson.Str(prompt))
            ++ typeEx.map(t => "type" -> writeTypeExpr(t))
        )
      case ValueRefDto(path) =>
        ujson.Obj("value" -> ujson.Str("valueRef"), "path" -> ujson.Str(path))
      case ConstantRefDto(path) =>
        ujson.Obj("value" -> ujson.Str("constantRef"), "path" -> ujson.Str(path))
      case GetValueDto(source, keyword, ref) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "value" -> ujson.Str("get"),
            "source" -> ujson.Str(source)
          )
            ++ keyword.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
            ++ Seq("ref" -> (ujson.Str(ref): ujson.Value))
        )
      case BooleanLiteralDto(b) =>
        ujson.Obj("value" -> ujson.Str("boolLiteral"), "bool" -> ujson.Bool(b))
      case ComparisonDto(op, left, right) =>
        ujson.Obj(
          "value" -> ujson.Str("comparison"),
          "op" -> ujson.Str(op),
          "left" -> writeValue(left),
          "right" -> writeValue(right)
        )
      case LogicalDto(op, left, right) =>
        ujson.Obj(
          "value" -> ujson.Str("logical"),
          "op" -> ujson.Str(op),
          "left" -> writeValue(left),
          "right" -> writeValue(right)
        )
      case NotDto(expr) =>
        ujson.Obj("value" -> ujson.Str("not"), "expr" -> writeValue(expr))
      case InvariantConditionDto(inv, argument) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "value" -> ujson.Str("invariantCondition"),
            "invariant" -> ujson.Str(inv)
          ) ++ argument.map(a => "argument" -> (writeValue(a): ujson.Value))
        )
      case ConstructorValueDto(refKind, ref, args) =>
        ujson.Obj(
          "value" -> ujson.Str("constructor"),
          "refKind" -> ujson.Str(refKind),
          "ref" -> ujson.Str(ref),
          "args" -> ujson.Arr.from(args.map { a =>
            ujson.Obj.from(
              a.name.map(n => "name" -> (ujson.Str(n): ujson.Value)).toSeq
                ++ Seq("value" -> (writeValue(a.value): ujson.Value))
            )
          })
        )
      case AskValueDto(query, processor, processorKind) =>
        ujson.Obj(
          "value" -> ujson.Str("ask"),
          "query" -> ujson.Str(query),
          "processor" -> ujson.Str(processor),
          "processorKind" -> ujson.Str(processorKind)
        )
      case CallValueDto(function, args) =>
        ujson.Obj(
          "value" -> ujson.Str("call"),
          "function" -> ujson.Str(function),
          "args" -> ujson.Arr.from(args.map { a =>
            ujson.Obj.from(
              a.name.map(n => "name" -> (ujson.Str(n): ujson.Value)).toSeq
                ++ Seq("value" -> (writeValue(a.value): ujson.Value))
            )
          })
        )
      case SelfValueDto(field) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("value" -> ujson.Str("self"))
            ++ field.map(f => "field" -> (ujson.Str(f): ujson.Value))
        )
      case InitiateValueDto(processor, processorKind, args) =>
        ujson.Obj(
          "value" -> ujson.Str("initiate"),
          "processor" -> ujson.Str(processor),
          "processorKind" -> ujson.Str(processorKind),
          "args" -> ujson.Arr.from(args.map { a =>
            ujson.Obj.from(
              a.name.map(n => "name" -> (ujson.Str(n): ujson.Value)).toSeq
                ++ Seq("value" -> (writeValue(a.value): ujson.Value))
            )
          })
        )
  end writeValue

  // A54: ujson <-> a message operand (bare ref or inline constructor). A constructor object carries a
  // `"value": "constructor"` key; a bare ref carries `ref`/`kind`.
  private def readMsgOperand(v: ujson.Value): MsgOperandDto =
    if v.obj.contains("value") then
      readValue(v) match
        case c: ConstructorValueDto => c
        case other =>
          throw new IllegalArgumentException(
            s"message operand must be a ref or constructor, got $other"
          )
    else msgRef(v)

  private def writeMsgOperand(dto: MsgOperandDto): ujson.Value =
    dto match
      case c: ConstructorValueDto => writeValue(c)
      case mr: MessageRefDto      => msgRefJs(mr)

  private def readStatement(v: ujson.Value): StatementDto =
    v match
      case ujson.Str(s) => PromptStmtDto(s)
      case _ =>
        val m = v.obj
        m("kind").str match
          case "prompt" => PromptStmtDto(m("text").str)
          case "comment" =>
            CommentStmtDto(m("text").str, m.get("inline").exists(_.bool))
          case "error" => ErrorStmtDto(m("message").str)
          case "let" =>
            LetStmtDto(m("name").str, m.get("type").map(_.str), readValue(m("expression")))
          case "code" => CodeStmtDto(m("language").str, m("body").str)
          case "require" =>
            RequireStmtDto(
              m.get("condition").map(_.str),
              m.get("invariant").map(_.str),
              m.get("expression").map(readValue),
              m.get("argument").map(readValue)
            )
          case "set" =>
            SetStmtDto(m.get("field").map(_.str), m.get("state").map(_.str), readValue(m("value")))
          case "send" => SendStmtDto(readMsgOperand(m("message")), m("to").str, m("portlet").str)
          case "morph" =>
            MorphStmtDto(m("entity").str, m("state").str, readMsgOperand(m("value")))
          case "become" => BecomeStmtDto(m("entity").str, m("handler").str)
          case "tell" =>
            TellStmtDto(
              readMsgOperand(m("message")),
              m("to").str,
              m("processor").str,
              m.get("by").map(_.str)
            )
          case "yield" => YieldStmtDto(readMsgOperand(m("message")))
          case "reply" => ReplyStmtDto(readMsgOperand(m("message")))
          case "when" =>
            WhenStmtDto(
              m.get("condition").map(_.str),
              m.get("conditionIdentifier").map(_.str),
              readStmts(m.get("then")),
              readStmts(m.get("else")),
              m.get("expression").map(readValue)
            )
          case "match" =>
            val cases = m
              .get("cases")
              .map(
                _.arr
                  .map(c =>
                    MatchCaseDto(
                      readMatchPattern(c.obj("pattern")),
                      c.obj.get("guard").map(readValue),
                      readStmts(c.obj.get("statements"))
                    )
                  )
                  .toSeq
              )
              .getOrElse(Nil)
            MatchStmtDto(readValue(m("subject")), cases, readStmts(m.get("default")))
          case "foreach" =>
            ForeachStmtDto(
              m("element").str,
              m.get("valueElement").map(_.str),
              m.get("field").map(_.str),
              m.get("local").map(_.str),
              readStmts(m.get("do"))
            )
          case "put"    => PutStmtDto(readValue(m("value")), m("output").str)
          case "return" => ReturnStmtDto(readValue(m("value")))
          case "terminate" =>
            val args = m
              .get("args")
              .map(
                _.arr
                  .map(a =>
                    ConstructorArgDto(a.obj.get("name").map(_.str), readValue(a.obj("value")))
                  )
                  .toSeq
              )
              .getOrElse(Nil)
            TerminateStmtDto(readValue(m("target")), args)
          case other => throw new IllegalArgumentException(s"Unknown statement kind: '$other'")
    end match
  end readStatement

  private def stmtArr(stmts: Seq[StatementDto]): ujson.Value =
    ujson.Arr.from(stmts.map(writeStatement))

  private def writeStatement(dto: StatementDto): ujson.Value =
    dto match
      case PromptStmtDto(text) =>
        ujson.Obj("kind" -> ujson.Str("prompt"), "text" -> ujson.Str(text))
      case CommentStmtDto(text, inline) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("comment"), "text" -> ujson.Str(text))
            ++ (if inline then Seq("inline" -> (ujson.Bool(true): ujson.Value)) else Nil)
        )
      case ErrorStmtDto(message) =>
        ujson.Obj("kind" -> ujson.Str("error"), "message" -> ujson.Str(message))
      case LetStmtDto(name, t, e) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("let"), "name" -> ujson.Str(name))
            ++ t.map(x => "type" -> (ujson.Str(x): ujson.Value))
            ++ Seq("expression" -> (writeValue(e): ujson.Value))
        )
      case CodeStmtDto(language, body) =>
        ujson.Obj(
          "kind" -> ujson.Str("code"),
          "language" -> ujson.Str(language),
          "body" -> ujson.Str(body)
        )
      case RequireStmtDto(condition, invariant, expression, argument) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("require"))
            ++ condition.map(x => "condition" -> (ujson.Str(x): ujson.Value))
            ++ invariant.map(x => "invariant" -> (ujson.Str(x): ujson.Value))
            ++ expression.map(x => "expression" -> (writeValue(x): ujson.Value))
            ++ argument.map(x => "argument" -> (writeValue(x): ujson.Value))
        )
      case SetStmtDto(field, state, value) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("set"))
            ++ field.map(x => "field" -> (ujson.Str(x): ujson.Value))
            ++ state.map(x => "state" -> (ujson.Str(x): ujson.Value))
            ++ Seq("value" -> (writeValue(value): ujson.Value))
        )
      case SendStmtDto(message, to, portlet) =>
        ujson.Obj(
          "kind" -> ujson.Str("send"),
          "message" -> writeMsgOperand(message),
          "to" -> ujson.Str(to),
          "portlet" -> ujson.Str(portlet)
        )
      case MorphStmtDto(entity, state, value) =>
        ujson.Obj(
          "kind" -> ujson.Str("morph"),
          "entity" -> ujson.Str(entity),
          "state" -> ujson.Str(state),
          "value" -> writeMsgOperand(value)
        )
      case BecomeStmtDto(entity, handler) =>
        ujson.Obj(
          "kind" -> ujson.Str("become"),
          "entity" -> ujson.Str(entity),
          "handler" -> ujson.Str(handler)
        )
      case TellStmtDto(message, to, processor, by) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("tell"),
            "message" -> writeMsgOperand(message),
            "to" -> ujson.Str(to),
            "processor" -> ujson.Str(processor)
          ) ++ by.map(x => "by" -> (ujson.Str(x): ujson.Value))
        )
      case YieldStmtDto(message) =>
        ujson.Obj("kind" -> ujson.Str("yield"), "message" -> writeMsgOperand(message))
      case ReplyStmtDto(message) =>
        ujson.Obj("kind" -> ujson.Str("reply"), "message" -> writeMsgOperand(message))
      case WhenStmtDto(condition, conditionId, thenS, elseS, expression) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("when"))
            ++ condition.map(x => "condition" -> (ujson.Str(x): ujson.Value))
            ++ conditionId.map(x => "conditionIdentifier" -> (ujson.Str(x): ujson.Value))
            ++ expression.map(x => "expression" -> (writeValue(x): ujson.Value))
            ++ Seq[(String, ujson.Value)](
              "then" -> stmtArr(thenS),
              "else" -> stmtArr(elseS)
            )
        )
      case MatchStmtDto(subject, cases, default) =>
        ujson.Obj(
          "kind" -> ujson.Str("match"),
          "subject" -> writeValue(subject),
          "cases" -> ujson.Arr.from(
            cases.map(c =>
              ujson.Obj.from(
                Seq[(String, ujson.Value)](
                  "pattern" -> writeMatchPattern(c.pattern)
                )
                  ++ c.guard.map(g => "guard" -> (writeValue(g): ujson.Value))
                  ++ Seq[(String, ujson.Value)]("statements" -> stmtArr(c.statements))
              )
            )
          ),
          "default" -> stmtArr(default)
        )
      case ForeachStmtDto(element, valueElement, field, local, doStatements) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("foreach"),
            "element" -> ujson.Str(element)
          )
            ++ valueElement.map(x => "valueElement" -> (ujson.Str(x): ujson.Value))
            ++ field.map(x => "field" -> (ujson.Str(x): ujson.Value))
            ++ local.map(x => "local" -> (ujson.Str(x): ujson.Value))
            ++ Seq("do" -> (stmtArr(doStatements): ujson.Value))
        )
      case PutStmtDto(value, output) =>
        ujson.Obj(
          "kind" -> ujson.Str("put"),
          "value" -> writeValue(value),
          "output" -> ujson.Str(output)
        )
      case ReturnStmtDto(value) =>
        ujson.Obj("kind" -> ujson.Str("return"), "value" -> writeValue(value))
      case TerminateStmtDto(target, args) =>
        ujson.Obj(
          "kind" -> ujson.Str("terminate"),
          "target" -> writeValue(target),
          "args" -> ujson.Arr.from(args.map { a =>
            ujson.Obj.from(
              a.name.map(n => "name" -> (ujson.Str(n): ujson.Value)).toSeq
                ++ Seq("value" -> (writeValue(a.value): ujson.Value))
            )
          })
        )
  end writeStatement

  // Given ReadWriters. These are lazy (Scala 3 parameterless givens), so the
  // mutual recursion FieldDto <-> TypeExprDto resolves correctly.
  // ujson <-> InteractionDto. Containers nest `interactions` recursively.

  // RefDto is written by THIS hand-written codec rather than by its derived one, so a field added
  // to the case class does not reach the wire until it is added here too — which is how the
  // group/input/output alias went on being dropped after RefDto had grown a `keyword`.
  private def refJs(r: RefDto): ujson.Value =
    ujson.Obj.from(
      Seq[(String, ujson.Value)]("kind" -> ujson.Str(r.kind), "path" -> ujson.Str(r.path))
        ++ r.keyword.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
    )
  private def readRef(v: ujson.Value): RefDto =
    RefDto(v.obj("kind").str, v.obj("path").str, v.obj.get("keyword").map(_.str))
  private def readIxns(o: Option[ujson.Value]): Seq[InteractionDto] =
    o.map(_.arr.map(readInteraction).toSeq).getOrElse(Nil)

  private def readInteraction(v: ujson.Value): InteractionDto =
    val m = v.obj
    m("kind").str match
      case "vague" => VagueIxnDto(m("from").str, m("relationship").str, m("to").str)
      case "sendMessage" =>
        SendMessageIxnDto(readRef(m("from")), msgRef(m("message")), m("to").str, m("processor").str)
      case "arbitrary" =>
        ArbitraryIxnDto(readRef(m("from")), m("relationship").str, readRef(m("to")))
      case "self" => SelfIxnDto(readRef(m("from")), m("relationship").str)
      case "focusOnGroup" =>
        FocusOnGroupIxnDto(m("user").str, m("group").str, m.get("keyword").map(_.str))
      case "directToURL" => DirectToURLIxnDto(m("user").str, m("url").str)
      case "showOutput" =>
        ShowOutputIxnDto(
          m("output").str,
          m("relationship").str,
          m("user").str,
          m.get("keyword").map(_.str)
        )
      case "selectInput" =>
        SelectInputIxnDto(m("user").str, m("input").str, m.get("keyword").map(_.str))
      case "takeInput" =>
        TakeInputIxnDto(m("user").str, m("input").str, m.get("keyword").map(_.str))
      case "refusal"    => RefusalIxnDto(readRef(m("from")), m("user").str, m("reason").str)
      case "parallel"   => ParallelIxnDto(readIxns(m.get("interactions")))
      case "sequential" => SequentialIxnDto(readIxns(m.get("interactions")))
      case "optional"   => OptionalIxnDto(readIxns(m.get("interactions")))
      case other        => throw new IllegalArgumentException(s"Unknown interaction kind: '$other'")
    end match
  end readInteraction

  private def ixnArr(ixns: Seq[InteractionDto]): ujson.Value =
    ujson.Arr.from(ixns.map(writeInteraction))

  private def writeInteraction(dto: InteractionDto): ujson.Value =
    dto match
      case VagueIxnDto(from, rel, to) =>
        ujson.Obj(
          "kind" -> ujson.Str("vague"),
          "from" -> ujson.Str(from),
          "relationship" -> ujson.Str(rel),
          "to" -> ujson.Str(to)
        )
      case SendMessageIxnDto(from, msg, to, proc) =>
        ujson.Obj(
          "kind" -> ujson.Str("sendMessage"),
          "from" -> refJs(from),
          "message" -> msgRefJs(msg),
          "to" -> ujson.Str(to),
          "processor" -> ujson.Str(proc)
        )
      case ArbitraryIxnDto(from, rel, to) =>
        ujson.Obj(
          "kind" -> ujson.Str("arbitrary"),
          "from" -> refJs(from),
          "relationship" -> ujson.Str(rel),
          "to" -> refJs(to)
        )
      case SelfIxnDto(from, rel) =>
        ujson.Obj(
          "kind" -> ujson.Str("self"),
          "from" -> refJs(from),
          "relationship" -> ujson.Str(rel)
        )
      case FocusOnGroupIxnDto(user, group, kw) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("focusOnGroup"),
            "user" -> ujson.Str(user),
            "group" -> ujson.Str(group)
          ) ++ kw.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case DirectToURLIxnDto(user, url) =>
        ujson.Obj(
          "kind" -> ujson.Str("directToURL"),
          "user" -> ujson.Str(user),
          "url" -> ujson.Str(url)
        )
      case ShowOutputIxnDto(output, rel, user, kw) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("showOutput"),
            "output" -> ujson.Str(output),
            "relationship" -> ujson.Str(rel),
            "user" -> ujson.Str(user)
          ) ++ kw.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case SelectInputIxnDto(user, input, kw) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("selectInput"),
            "user" -> ujson.Str(user),
            "input" -> ujson.Str(input)
          ) ++ kw.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case TakeInputIxnDto(user, input, kw) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("takeInput"),
            "user" -> ujson.Str(user),
            "input" -> ujson.Str(input)
          ) ++ kw.map(k => "keyword" -> (ujson.Str(k): ujson.Value))
        )
      case RefusalIxnDto(from, user, reason) =>
        ujson.Obj(
          "kind" -> ujson.Str("refusal"),
          "from" -> refJs(from),
          "user" -> ujson.Str(user),
          "reason" -> ujson.Str(reason)
        )
      case ParallelIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("parallel"), "interactions" -> ixnArr(ixns))
      case SequentialIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("sequential"), "interactions" -> ixnArr(ixns))
      case OptionalIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("optional"), "interactions" -> ixnArr(ixns))
    end match
  end writeInteraction

  /** ujson <-> ArgDto. A function/saga `input`/`output` arg is a bare string (a type ref — the A9
    * canonical form) or a bare array of fields (the deprecated inline aggregation, per the
    * `"input"?: [<field>]` wire schema). Reading also tolerates the legacy object form `{ "ref"?,
    * "fields"? }`. (macroRW alone could only read/write that object form, so the documented
    * bare-array/string shapes failed to parse — this reconciles read and write.)
    */
  private def readArg(v: ujson.Value): ArgDto = v match
    case ujson.Str(s)     => ArgDto(ref = Some(s))
    case ujson.Arr(items) => ArgDto(fields = items.map(j => readJson[FieldDto](j)).toSeq)
    case obj: ujson.Obj =>
      val m = obj.obj
      ArgDto(
        ref = m.get("ref").map(_.str),
        fields = m.get("fields").map(_.arr.map(j => readJson[FieldDto](j)).toSeq).getOrElse(Nil)
      )
    case other =>
      throw new IllegalArgumentException(
        s"Invalid arg: expected a type-ref string or a field array, got: ${other.getClass.getSimpleName}"
      )
  end readArg

  private def writeArg(dto: ArgDto): ujson.Value = dto.ref match
    case Some(r) => ujson.Str(r)
    case None    => ujson.Arr.from(dto.fields.map(f => writeJs(f)))
  end writeArg

  /** ujson <-> [[ContentDto]]: a `kind`-tagged object, one entry of an ordered `contents` array.
    *
    * The body is delegated to the DTO's own (macro-derived) codec and the `kind` tag is spliced on
    * the front, so this stays a thin dispatch rather than a second hand-written serializer for
    * thirty-odd DTOs. Reading strips `kind` before delegating.
    *
    * Two kinds are not one-to-one with a DTO: the four message use cases share [[MessageDto]], and
    * inlet/outlet share [[PortletDto]]. For those the tag IS the discriminator, so it is lifted
    * into `usecase`/`direction` on the way in and stripped on the way out to avoid writing it
    * twice.
    */
  /** The key a `contents` entry's kind tag lives under.
    *
    * `$kind` rather than the more obvious `kind` because two content DTOs — [[OnClauseDto]] and
    * [[SchemaDto]] — already carry a `kind` FIELD of their own, and a tag under the same key
    * silently overwrote it (`ujson.Obj.from` keeps the last of a duplicate pair), so an on-clause
    * went out as `"kind": "message"` and came back an unknown content kind. A `$`-prefixed key
    * cannot collide with any DTO field, present or future, and mirrors upickle's own `$type` in
    * reading as structural metadata rather than as model data.
    */
  val ContentTag: String = "$kind"

  private def readContent(v: ujson.Value): ContentDto =
    val obj = v.obj
    val kind = obj
      .get(ContentTag)
      .map(_.str)
      .getOrElse(
        throw new IllegalArgumentException(
          s"A `contents` entry needs a `$ContentTag`: ${ujson.write(v).take(120)}"
        )
      )
    val body: ujson.Value =
      ujson.Obj.from(obj.toSeq.filter(kv => kv._1 != ContentTag && kv._1 != LocationTag))
    kind match
      case ContentKind.Domain         => readJson[DomainDto](body)
      case ContentKind.Module         => readJson[ModuleDto](body)
      case ContentKind.Context        => readJson[ContextDto](body)
      case ContentKind.Entity         => readJson[EntityDto](body)
      case ContentKind.Type           => readJson[TypeDefDto](body)
      case ContentKind.State          => readJson[StateDto](body)
      case ContentKind.Correlation    => readJson[CorrelationDto](body)
      case ContentKind.Handler        => readJson[HandlerDto](body)
      case ContentKind.OnClause       => readJson[OnClauseDto](body)
      case ContentKind.Function       => readJson[FunctionDto](body)
      case ContentKind.Adaptor        => readJson[AdaptorDto](body)
      case ContentKind.Streamlet      => readJson[StreamletDto](body)
      case ContentKind.Projector      => readJson[ProjectorDto](body)
      case ContentKind.Repository     => readJson[RepositoryDto](body)
      case ContentKind.Schema         => readJson[SchemaDto](body)
      case ContentKind.Connector      => readJson[ConnectorDto](body)
      case ContentKind.Relationship   => readJson[RelationshipDto](body)
      case ContentKind.Saga           => readJson[SagaDto](body)
      case ContentKind.SagaStep       => readJson[SagaStepDto](body)
      case ContentKind.Epic           => readJson[EpicDto](body)
      case ContentKind.UseCase        => readJson[UseCaseDto](body)
      case ContentKind.Group          => readJson[GroupDto](body)
      case ContentKind.ContainedGroup => readJson[ContainedGroupDto](body)
      case ContentKind.Input          => readJson[InputDto](body)
      case ContentKind.Output         => readJson[OutputDto](body)
      case ContentKind.Author         => readJson[AuthorDto](body)
      case ContentKind.User           => readJson[UserDto](body)
      case ContentKind.Invariant      => readJson[InvariantDto](body)
      case ContentKind.Constant       => readJson[ConstantDto](body)
      case ContentKind.Comment        => readJson[CommentDto](body)
      case ContentKind.Version        => readJson[VersionDto](body)
      case ContentKind.Copyright      => readJson[CopyrightDto](body)
      case ContentKind.Field          => readJson[FieldDto](body)
      // MethodDto has no derived codec — its `args` need the hand-written pair.
      case ContentKind.Method => readMethod(body)
      case ContentKind.Term   => readJson[TermDto](body)
      case ContentKind.Requires => RequiresDto(readArg(body.obj("arg")))
      case ContentKind.Returns  => ReturnsDto(readArg(body.obj("arg")))
      case ContentKind.Interaction =>
        InteractionContentDto(readInteraction(body.obj("interaction")))
      case ContentKind.Include =>
        IncludeContentDto(body.obj("origin").str, readContents(body.obj.get("contents")))
      case ContentKind.BASTImport =>
        BASTImportContentDto(
          body.obj("path").str,
          body.obj.get("importKind").map(_.str),
          body.obj.get("selector").map(_.str),
          body.obj.get("alias").map(_.str),
          readContents(body.obj.get("contents"))
        )
      case k if ContentKind.messageKinds.contains(k) =>
        readJson[MessageDto](body).copy(usecase = Some(k))
      case k @ (ContentKind.Inlet | ContentKind.Outlet) =>
        readJson[PortletDto](body).copy(direction = Some(k))
      case other =>
        throw new IllegalArgumentException(s"Unknown `contents` kind: '$other'")
    end match
  end readContent

  private def writeContent(dto: ContentDto): ujson.Value =
    val tagged: (String, ujson.Value) = dto match
      case d: DomainDto         => (ContentKind.Domain, writeJs(d))
      case d: ModuleDto         => (ContentKind.Module, writeJs(d))
      case d: ContextDto        => (ContentKind.Context, writeJs(d))
      case d: EntityDto         => (ContentKind.Entity, writeJs(d))
      case d: TypeDefDto        => (ContentKind.Type, writeJs(d))
      case d: StateDto          => (ContentKind.State, writeJs(d))
      case d: CorrelationDto    => (ContentKind.Correlation, writeJs(d))
      case d: HandlerDto        => (ContentKind.Handler, writeJs(d))
      case d: OnClauseDto       => (ContentKind.OnClause, writeJs(d))
      case d: FunctionDto       => (ContentKind.Function, writeJs(d))
      case d: AdaptorDto        => (ContentKind.Adaptor, writeJs(d))
      case d: StreamletDto      => (ContentKind.Streamlet, writeJs(d))
      case d: ProjectorDto      => (ContentKind.Projector, writeJs(d))
      case d: RepositoryDto     => (ContentKind.Repository, writeJs(d))
      case d: SchemaDto         => (ContentKind.Schema, writeJs(d))
      case d: ConnectorDto      => (ContentKind.Connector, writeJs(d))
      case d: RelationshipDto   => (ContentKind.Relationship, writeJs(d))
      case d: SagaDto           => (ContentKind.Saga, writeJs(d))
      case d: SagaStepDto       => (ContentKind.SagaStep, writeJs(d))
      case d: EpicDto           => (ContentKind.Epic, writeJs(d))
      case d: UseCaseDto        => (ContentKind.UseCase, writeJs(d))
      case d: GroupDto          => (ContentKind.Group, writeJs(d))
      case d: ContainedGroupDto => (ContentKind.ContainedGroup, writeJs(d))
      case d: InputDto          => (ContentKind.Input, writeJs(d))
      case d: OutputDto         => (ContentKind.Output, writeJs(d))
      case d: AuthorDto         => (ContentKind.Author, writeJs(d))
      case d: UserDto           => (ContentKind.User, writeJs(d))
      case d: InvariantDto      => (ContentKind.Invariant, writeJs(d))
      case d: ConstantDto       => (ContentKind.Constant, writeJs(d))
      case d: CommentDto        => (ContentKind.Comment, writeJs(d))
      case d: VersionDto        => (ContentKind.Version, writeJs(d))
      case d: CopyrightDto      => (ContentKind.Copyright, writeJs(d))
      case d: FieldDto          => (ContentKind.Field, writeJs(d))
      case d: MethodDto         => (ContentKind.Method, writeMethod(d))
      case d: TermDto           => (ContentKind.Term, writeJs(d))
      case d: RequiresDto => (ContentKind.Requires, ujson.Obj("arg" -> writeArg(d.arg)))
      case d: ReturnsDto  => (ContentKind.Returns, ujson.Obj("arg" -> writeArg(d.arg)))
      case d: InteractionContentDto =>
        (ContentKind.Interaction, ujson.Obj("interaction" -> writeInteraction(d.interaction)))
      case d: IncludeContentDto =>
        (
          ContentKind.Include,
          ujson.Obj("origin" -> ujson.Str(d.origin), "contents" -> writeContents(d.contents))
        )
      case d: BASTImportContentDto =>
        (
          ContentKind.BASTImport,
          ujson.Obj.from(
            Seq[(String, ujson.Value)]("path" -> ujson.Str(d.path))
              ++ d.importKind.map(k => "importKind" -> (ujson.Str(k): ujson.Value))
              ++ d.selector.map(k => "selector" -> (ujson.Str(k): ujson.Value))
              ++ d.alias.map(k => "alias" -> (ujson.Str(k): ujson.Value))
              ++ Seq("contents" -> (writeContents(d.contents): ujson.Value))
          )
        )
      // The tag carries the discriminator, so it is cleared from the body rather than written
      // twice. A message with no use case can only come from a bucketed document; the bucket said
      // which it was, and the emitter always sets it.
      case d: MessageDto =>
        (d.usecase.getOrElse(ContentKind.Command), writeJs(d.copy(usecase = None)))
      case d: PortletDto =>
        (d.direction.getOrElse(ContentKind.Inlet), writeJs(d.copy(direction = None)))
    val (kind, body) = tagged
    ujson.Obj.from((ContentTag -> (ujson.Str(kind): ujson.Value)) +: body.obj.toSeq)
  end writeContent

  /** The key an entry's `[offset, endOffset]` lives under.
    *
    * `$`-prefixed for the same reason as [[ContentTag]]: it is structural metadata about the entry
    * rather than a field of the model, and it can never collide with a DTO's own field.
    */
  val LocationTag: String = "$at"

  private def readContents(o: Option[ujson.Value]): Seq[ContentEntry] =
    o.map(_.arr.map(readContentEntry).toSeq).getOrElse(Nil)

  private def writeContents(cs: Seq[ContentEntry]): ujson.Value =
    ujson.Arr.from(cs.map(writeContentEntry))

  private def readContentEntry(v: ujson.Value): ContentEntry =
    val at = v.obj.get(LocationTag).flatMap { loc =>
      val a = loc.arr
      if a.sizeIs == 2 then Some((a(0).num.toInt, a(1).num.toInt)) else None
    }
    ContentEntry(readContent(v), at)

  private def writeContentEntry(e: ContentEntry): ujson.Value =
    val obj = writeContent(e.content).obj
    e.at match
      case None               => ujson.Obj.from(obj.toSeq)
      case Some((start, end)) =>
        // After `$kind`, so an entry reads "what it is, then where it came from".
        val (tag, rest) = obj.toSeq.partition(_._1 == ContentTag)
        val loc: (String, ujson.Value) =
          LocationTag -> ujson.Arr(ujson.Num(start.toDouble), ujson.Num(end.toDouble))
        ujson.Obj.from((tag :+ loc) ++ rest)
  end writeContentEntry

  given contentEntryRW: ReadWriter[ContentEntry] =
    readwriter[ujson.Value].bimap[ContentEntry](writeContentEntry, readContentEntry)
  given locationsRW: ReadWriter[LocationsDto] = macroRW
  given typeExprRW: ReadWriter[TypeExprDto] =
    readwriter[ujson.Value].bimap[TypeExprDto](writeTypeExpr, readTypeExpr)
  given statementRW: ReadWriter[StatementDto] =
    readwriter[ujson.Value].bimap[StatementDto](writeStatement, readStatement)
  given interactionRW: ReadWriter[InteractionDto] =
    readwriter[ujson.Value].bimap[InteractionDto](writeInteraction, readInteraction)
  given userStoryRW: ReadWriter[UserStoryDto] = macroRW
  given useCaseRW: ReadWriter[UseCaseDto] = macroRW
  given epicRW: ReadWriter[EpicDto] = macroRW
  given argRW: ReadWriter[ArgDto] =
    readwriter[ujson.Value].bimap[ArgDto](writeArg, readArg)
  given requiresDtoRW: ReadWriter[RequiresDto] = macroRW
  given returnsDtoRW: ReadWriter[ReturnsDto] = macroRW
  given functionRW: ReadWriter[FunctionDto] = macroRW
  given portletRW: ReadWriter[PortletDto] = macroRW
  given connectorDtoRW: ReadWriter[ConnectorDto] = macroRW
  given adaptorRW: ReadWriter[AdaptorDto] = macroRW
  given streamletRW: ReadWriter[StreamletDto] = macroRW
  given relationshipRW: ReadWriter[RelationshipDto] = macroRW
  given projectorRW: ReadWriter[ProjectorDto] = macroRW
  given schemaDtoRW: ReadWriter[SchemaDto] = macroRW
  given repositoryRW: ReadWriter[RepositoryDto] = macroRW
  given sagaStepRW: ReadWriter[SagaStepDto] = macroRW
  given sagaRW: ReadWriter[SagaDto] = macroRW
  given putOutRW: ReadWriter[PutOutDto] = macroRW
  given inputRW: ReadWriter[InputDto] = macroRW
  given outputRW: ReadWriter[OutputDto] = macroRW
  given containedGroupRW: ReadWriter[ContainedGroupDto] = macroRW
  given groupRW: ReadWriter[GroupDto] = macroRW
  given commentDtoRW: ReadWriter[CommentDto] = macroRW
  given termDtoRW: ReadWriter[TermDto] = macroRW
  given optionDtoRW: ReadWriter[OptionDto] = macroRW
  given attachmentDtoRW: ReadWriter[AttachmentDto] = macroRW
  given figmaRefDtoRW: ReadWriter[FigmaRefDto] = macroRW
  given metaItemRW: ReadWriter[MetaItemDto] = macroRW
  given metaDtoRW: ReadWriter[MetaDto] = macroRW
  given fieldRW: ReadWriter[FieldDto] = macroRW
  given messageRefRW: ReadWriter[MessageRefDto] = macroRW
  given refDtoRW: ReadWriter[RefDto] = macroRW
  given onFromRW: ReadWriter[OnFromDto] = macroRW
  // Task 3: `on init`/`on term` `parameters`. `MethodDto.args` (the same DTO) never needed this --
  // RecordDto/MethodDto are read/written by the hand-rolled readMethod/writeMethod inside
  // readTypeExpr/writeTypeExpr, not macroRW -- but OnClauseDto IS macroRW-derived, so its
  // `Seq[MethodArgDto]` field needs a given Reader/Writer for macroRW to find.
  given methodArgDtoRW: ReadWriter[MethodArgDto] = macroRW
  given onClauseRW: ReadWriter[OnClauseDto] = macroRW
  given handlerRW: ReadWriter[HandlerDto] = macroRW
  // A28: InvariantDto (macroRW) may carry a structured BooleanExpression in `expression: Option[
  // ValueDto]`. ValueDto is read/written by the manual readValue/writeValue codec, so bridge it into
  // a ReadWriter for macroRW derivation.
  given valueDtoRW: ReadWriter[ValueDto] =
    readwriter[ujson.Value].bimap[ValueDto](writeValue, readValue)
  given invariantBlockRW: ReadWriter[InvariantBlockDto] = macroRW
  given invariantRW: ReadWriter[InvariantDto] = macroRW
  given versionRW: ReadWriter[VersionDto] = macroRW
  given copyrightRW: ReadWriter[CopyrightDto] = macroRW
  given constantRW: ReadWriter[ConstantDto] = macroRW
  given userRW: ReadWriter[UserDto] = macroRW
  given stateRW: ReadWriter[StateDto] = macroRW
  given correlationRW: ReadWriter[CorrelationDto] = macroRW
  given messageRW: ReadWriter[MessageDto] = macroRW
  given typeDefRW: ReadWriter[TypeDefDto] = macroRW
  given entityRW: ReadWriter[EntityDto] = macroRW
  given contextRW: ReadWriter[ContextDto] = macroRW
  given authorRW: ReadWriter[AuthorDto] = macroRW
  given domainRW: ReadWriter[DomainDto] = macroRW
  given moduleRW: ReadWriter[ModuleDto] = macroRW
  given rootRW: ReadWriter[RootDto] = macroRW

  /** Parse a JSON string into the wire model. Throws on malformed JSON or an unknown
    * type-expression kind; [[com.ossuminc.riddl.RiddlLib.parseJson]] catches and converts to a
    * clean failure.
    */
  def readRoot(json: String): RootDto = readJson[RootDto](json)

  /** Serialize the wire model back to JSON. `indent` = 2 for pretty-printed output, -1 for compact.
    * Used by [[com.ossuminc.riddl.RiddlLib.root2Json]].
    */
  def writeRoot(dto: RootDto, indent: Int = 2): String = Pickle.write(dto, indent = indent)

end JsonModel
