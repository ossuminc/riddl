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

  /** `{ "kind": "Id", "entity": "<path>" }` — entity path required by builder. */
  case class IdDto(entity: Option[String] = None) extends TypeExprDto

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
    brief: Option[String] = None
  )

  /** `{ "kind": "Record", "fields": [ <field> ], "methods"?: [ <method> ] }` -> aggregate. */
  case class RecordDto(fields: Seq[FieldDto] = Nil, methods: Seq[MethodDto] = Nil)
      extends TypeExprDto

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

  case class RootDto(domains: Seq[DomainDto] = Nil, modules: Seq[ModuleDto] = Nil)

  /** A module groups domains (and authors): `{ "name": "M", "domains": [...], "authors": [...] }`
    * (Phase 6)
    */
  case class ModuleDto(
    name: String,
    brief: Option[String] = None,
    authors: Seq[AuthorDto] = Nil,
    domains: Seq[DomainDto] = Nil
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
    contexts: Seq[ContextDto] = Nil
  )

  /** `{ "name": "Shopper", "isA": "a person", "brief"?: ... }` (Phase 2) */
  case class UserDto(name: String, isA: String, brief: Option[String] = None)

  case class AuthorDto(
    name: String,
    fullName: String,
    email: String,
    organization: Option[String] = None,
    title: Option[String] = None
  )

  case class TypeDefDto(name: String, typeExpression: TypeExprDto, brief: Option[String] = None)

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
    handlers: Seq[HandlerDto] = Nil
  )

  case class MessageDto(name: String, brief: Option[String] = None, fields: Seq[FieldDto] = Nil)

  case class EntityDto(
    name: String,
    brief: Option[String] = None,
    state: Option[StateDto] = None,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    invariants: Seq[InvariantDto] = Nil
  )

  /** `{ "name": "MaxItems", "type": <typeExpr>, "value": "100", "brief"?: ... }` (Phase 2) */
  case class ConstantDto(
    name: String,
    `type`: TypeExprDto,
    value: String,
    brief: Option[String] = None
  )

  case class StateDto(name: String, recordType: String)

  case class HandlerDto(
    name: String,
    brief: Option[String] = None,
    onClauses: Seq[OnClauseDto] = Nil
  )

  /** `kind`: "message" | "init" | "other" | "term". For "message", `message` carries the message
    * ref + its kind. `statements` are tagged statement objects (or a bare string = prompt).
    */
  case class OnClauseDto(
    kind: String,
    message: Option[MessageRefDto] = None,
    statements: Seq[StatementDto] = Nil
  )

  case class MessageRefDto(ref: String, kind: String)

  case class InvariantDto(name: String, condition: String, brief: Option[String] = None)

  case class FieldDto(name: String, `type`: TypeExprDto, brief: Option[String] = None)

  /** A function: `input`/`output` are field lists (aggregations), `statements` is the body,
    * `functions` are nested. (Phase 3)
    */
  case class FunctionDto(
    name: String,
    brief: Option[String] = None,
    input: Seq[FieldDto] = Nil,
    output: Seq[FieldDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    statements: Seq[StatementDto] = Nil,
    functions: Seq[FunctionDto] = Nil
  )

  // ---------------------------------------------------------------------------
  // Statements (Phase 3) — each kind is its own tagged object. A bare JSON
  // string is shorthand for a `prompt` statement.
  // ---------------------------------------------------------------------------

  sealed trait StatementDto

  /** `"text"` or `{ "kind": "prompt", "text": "..." }` */
  case class PromptStmtDto(text: String) extends StatementDto

  /** `{ "kind": "error", "message": "..." }` */
  case class ErrorStmtDto(message: String) extends StatementDto

  /** `{ "kind": "let", "name": "x", "type"?: "<typePath>", "expression": "..." }` */
  case class LetStmtDto(name: String, `type`: Option[String], expression: String)
      extends StatementDto

  /** `{ "kind": "code", "language": "scala", "body": "..." }` */
  case class CodeStmtDto(language: String, body: String) extends StatementDto

  /** `{ "kind": "require", "condition": "..." }` or `{ ..., "invariant": "<name>" }` */
  case class RequireStmtDto(condition: Option[String], invariant: Option[String])
      extends StatementDto

  /** `{ "kind": "set", "field"|"state": "<path>", "value": "..." }` */
  case class SetStmtDto(field: Option[String], state: Option[String], value: String)
      extends StatementDto

  /** `{ "kind": "send", "message": <msgRef>, "to": "<path>", "portlet": "inlet"|"outlet" }` */
  case class SendStmtDto(message: MessageRefDto, to: String, portlet: String) extends StatementDto

  /** `{ "kind": "morph", "entity": "<path>", "state": "<path>", "value": <msgRef> }` */
  case class MorphStmtDto(entity: String, state: String, value: MessageRefDto) extends StatementDto

  /** `{ "kind": "become", "entity": "<path>", "handler": "<path>" }` */
  case class BecomeStmtDto(entity: String, handler: String) extends StatementDto

  /** `{ "kind": "tell", "message": <msgRef>, "to": "<path>", "processor":
    * "entity"|"context"|"projector"|"repository"|"adaptor" }`
    */
  case class TellStmtDto(message: MessageRefDto, to: String, processor: String) extends StatementDto

  /** `{ "kind": "reply", "message": <msgRef> }` */
  case class ReplyStmtDto(message: MessageRefDto) extends StatementDto

  /** `{ "kind": "when", "condition"|"conditionIdentifier": "...", "negated"?: bool, "then":
    * [<stmt>], "else"?: [<stmt>] }`
    */
  case class WhenStmtDto(
    condition: Option[String],
    conditionIdentifier: Option[String],
    negated: Boolean,
    thenStatements: Seq[StatementDto],
    elseStatements: Seq[StatementDto]
  ) extends StatementDto

  /** `{ "kind": "match", "expression": "...", "cases": [<matchCase>], "default"?: [<stmt>] }` */
  case class MatchStmtDto(expression: String, cases: Seq[MatchCaseDto], default: Seq[StatementDto])
      extends StatementDto

  /** `{ "pattern": "...", "statements": [<stmt>] }` */
  case class MatchCaseDto(pattern: String, statements: Seq[StatementDto])

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
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil
  )

  /** An inlet or outlet: `{ "name": "in", "type": "<typePath>", "brief"?: ... }` */
  case class PortletDto(name: String, `type`: String, brief: Option[String] = None)

  /** `{ "name": "C", "from": "<outletPath>", "to": "<inletPath>", "brief"?: ... }` */
  case class ConnectorDto(name: String, from: String, to: String, brief: Option[String] = None)

  /** `{ "name": "S", "shape": "source"|"sink"|"flow"|"merge"|"split"|"router"|"void", ... }` */
  case class StreamletDto(
    name: String,
    shape: String,
    brief: Option[String] = None,
    inlets: Seq[PortletDto] = Nil,
    outlets: Seq[PortletDto] = Nil,
    connectors: Seq[ConnectorDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    handlers: Seq[HandlerDto] = Nil
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
    brief: Option[String] = None
  )

  /** `{ "name": "P", "repository"?: "<path>", ... }` */
  case class ProjectorDto(
    name: String,
    brief: Option[String] = None,
    repository: Option[String] = None,
    types: Seq[TypeDefDto] = Nil,
    constants: Seq[ConstantDto] = Nil,
    functions: Seq[FunctionDto] = Nil,
    handlers: Seq[HandlerDto] = Nil
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
    brief: Option[String] = None
  )

  /** `{ "name": "Repo", "schema"?: <schema>, ... }` */
  case class RepositoryDto(
    name: String,
    brief: Option[String] = None,
    schema: Option[SchemaDto] = None,
    types: Seq[TypeDefDto] = Nil,
    handlers: Seq[HandlerDto] = Nil
  )

  // ---------------------------------------------------------------------------
  // Sagas (Phase 5)
  // ---------------------------------------------------------------------------

  /** `{ "name": "Reserve", "do": [<stmt>], "undo": [<stmt>], "brief"?: ... }` */
  case class SagaStepDto(
    name: String,
    `do`: Seq[StatementDto] = Nil,
    undo: Seq[StatementDto] = Nil,
    brief: Option[String] = None
  )

  /** `{ "name": "Booking", "input"?: [<field>], "output"?: [<field>], "types"?: [...], "steps":
    * [<sagaStep>], "brief"?: ... }`
    */
  case class SagaDto(
    name: String,
    brief: Option[String] = None,
    input: Seq[FieldDto] = Nil,
    output: Seq[FieldDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    steps: Seq[SagaStepDto] = Nil
  )

  // ---------------------------------------------------------------------------
  // Epics, use cases, interactions (Phase 7)
  // ---------------------------------------------------------------------------

  /** A generic definition reference: `{ "kind": "user"|"entity"|"context"|"group"|"output"|"input"|
    * "adaptor"|"projector", "path": "<path>" }`
    */
  case class RefDto(kind: String, path: String)

  /** `{ "user": "<userPath>", "capability": "...", "benefit": "..." }` */
  case class UserStoryDto(user: String, capability: String, benefit: String)

  /** An interaction step. Tagged by `kind`; containers nest `interactions`. */
  sealed trait InteractionDto
  case class VagueIxnDto(from: String, relationship: String, to: String) extends InteractionDto
  case class SendMessageIxnDto(from: RefDto, message: MessageRefDto, to: String, processor: String)
      extends InteractionDto
  case class ArbitraryIxnDto(from: RefDto, relationship: String, to: RefDto) extends InteractionDto
  case class SelfIxnDto(from: RefDto, relationship: String) extends InteractionDto
  case class FocusOnGroupIxnDto(user: String, group: String) extends InteractionDto
  case class DirectToURLIxnDto(user: String, url: String) extends InteractionDto
  case class ShowOutputIxnDto(output: String, relationship: String, user: String)
      extends InteractionDto
  case class SelectInputIxnDto(user: String, input: String) extends InteractionDto
  case class TakeInputIxnDto(user: String, input: String) extends InteractionDto
  case class ParallelIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto
  case class SequentialIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto
  case class OptionalIxnDto(interactions: Seq[InteractionDto]) extends InteractionDto

  /** `{ "name": "Pay", "userStory": <userStory>, "interactions": [<interaction>], "brief"?: ... }`
    */
  case class UseCaseDto(
    name: String,
    userStory: UserStoryDto,
    interactions: Seq[InteractionDto] = Nil,
    brief: Option[String] = None
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
    useCases: Seq[UseCaseDto] = Nil
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
    nounAlias: Option[String] = None,
    verbAlias: Option[String] = None,
    brief: Option[String] = None,
    inputs: Seq[InputDto] = Nil
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
    outputs: Seq[OutputDto] = Nil
  )

  /** `{ "name": "Sub", "group": "<groupPath>", "brief"?: ... }` */
  case class ContainedGroupDto(name: String, group: String, brief: Option[String] = None)

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
    outputs: Seq[OutputDto] = Nil
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
      CardinalityDto(
        m("cardinality").str,
        readTypeExpr(m("of")),
        m.get("min").map(_.num.toLong),
        m.get("max").map(_.num.toLong)
      )
    else
      m("kind").str match
        case "String" => StringDto(m.get("min").map(_.num.toLong), m.get("max").map(_.num.toLong))
        case "Id"     => IdDto(m.get("entity").map(_.str))
        // Argument-less predefined kinds (Phase 1 + Phase 2)
        case k @ ("UUID" | "Boolean" | "Date" | "TimeStamp" | "Integer" | "Whole" | "Natural" |
            "Number" | "Real" | "UserId" | "Abstract" | "Location" | "Nothing" | "Time" |
            "DateTime" | "Duration" | "Current" | "Length" | "Luminosity" | "Mass" | "Mole" |
            "Temperature") =>
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
          RecordDto(fields, methods)
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
        case other => throw new IllegalArgumentException(s"Unknown type expression kind: '$other'")
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
      case IdDto(entity) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("Id"))
            ++ entity.map(e => "entity" -> (ujson.Str(e): ujson.Value))
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
      case RecordDto(fields, methods) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)](
            "kind" -> ujson.Str("Record"),
            "fields" -> ujson.Arr.from(fields.map(f => writeJs(f)))
          ) ++ (if methods.nonEmpty then
                  Seq[(String, ujson.Value)]("methods" -> ujson.Arr.from(methods.map(writeMethod)))
                else Nil)
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

  private def readStatement(v: ujson.Value): StatementDto =
    v match
      case ujson.Str(s) => PromptStmtDto(s)
      case _ =>
        val m = v.obj
        m("kind").str match
          case "prompt" => PromptStmtDto(m("text").str)
          case "error"  => ErrorStmtDto(m("message").str)
          case "let"    => LetStmtDto(m("name").str, m.get("type").map(_.str), m("expression").str)
          case "code"   => CodeStmtDto(m("language").str, m("body").str)
          case "require" =>
            RequireStmtDto(m.get("condition").map(_.str), m.get("invariant").map(_.str))
          case "set" =>
            SetStmtDto(m.get("field").map(_.str), m.get("state").map(_.str), m("value").str)
          case "send"   => SendStmtDto(msgRef(m("message")), m("to").str, m("portlet").str)
          case "morph"  => MorphStmtDto(m("entity").str, m("state").str, msgRef(m("value")))
          case "become" => BecomeStmtDto(m("entity").str, m("handler").str)
          case "tell"   => TellStmtDto(msgRef(m("message")), m("to").str, m("processor").str)
          case "reply"  => ReplyStmtDto(msgRef(m("message")))
          case "when" =>
            WhenStmtDto(
              m.get("condition").map(_.str),
              m.get("conditionIdentifier").map(_.str),
              m.get("negated").exists(_.bool),
              readStmts(m.get("then")),
              readStmts(m.get("else"))
            )
          case "match" =>
            val cases = m
              .get("cases")
              .map(
                _.arr
                  .map(c => MatchCaseDto(c.obj("pattern").str, readStmts(c.obj.get("statements"))))
                  .toSeq
              )
              .getOrElse(Nil)
            MatchStmtDto(m("expression").str, cases, readStmts(m.get("default")))
          case other => throw new IllegalArgumentException(s"Unknown statement kind: '$other'")
    end match
  end readStatement

  private def stmtArr(stmts: Seq[StatementDto]): ujson.Value =
    ujson.Arr.from(stmts.map(writeStatement))

  private def writeStatement(dto: StatementDto): ujson.Value =
    dto match
      case PromptStmtDto(text) =>
        ujson.Obj("kind" -> ujson.Str("prompt"), "text" -> ujson.Str(text))
      case ErrorStmtDto(message) =>
        ujson.Obj("kind" -> ujson.Str("error"), "message" -> ujson.Str(message))
      case LetStmtDto(name, t, e) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("let"), "name" -> ujson.Str(name))
            ++ t.map(x => "type" -> (ujson.Str(x): ujson.Value))
            ++ Seq("expression" -> (ujson.Str(e): ujson.Value))
        )
      case CodeStmtDto(language, body) =>
        ujson.Obj(
          "kind" -> ujson.Str("code"),
          "language" -> ujson.Str(language),
          "body" -> ujson.Str(body)
        )
      case RequireStmtDto(condition, invariant) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("require"))
            ++ condition.map(x => "condition" -> (ujson.Str(x): ujson.Value))
            ++ invariant.map(x => "invariant" -> (ujson.Str(x): ujson.Value))
        )
      case SetStmtDto(field, state, value) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("set"))
            ++ field.map(x => "field" -> (ujson.Str(x): ujson.Value))
            ++ state.map(x => "state" -> (ujson.Str(x): ujson.Value))
            ++ Seq("value" -> (ujson.Str(value): ujson.Value))
        )
      case SendStmtDto(message, to, portlet) =>
        ujson.Obj(
          "kind" -> ujson.Str("send"),
          "message" -> msgRefJs(message),
          "to" -> ujson.Str(to),
          "portlet" -> ujson.Str(portlet)
        )
      case MorphStmtDto(entity, state, value) =>
        ujson.Obj(
          "kind" -> ujson.Str("morph"),
          "entity" -> ujson.Str(entity),
          "state" -> ujson.Str(state),
          "value" -> msgRefJs(value)
        )
      case BecomeStmtDto(entity, handler) =>
        ujson.Obj(
          "kind" -> ujson.Str("become"),
          "entity" -> ujson.Str(entity),
          "handler" -> ujson.Str(handler)
        )
      case TellStmtDto(message, to, processor) =>
        ujson.Obj(
          "kind" -> ujson.Str("tell"),
          "message" -> msgRefJs(message),
          "to" -> ujson.Str(to),
          "processor" -> ujson.Str(processor)
        )
      case ReplyStmtDto(message) =>
        ujson.Obj("kind" -> ujson.Str("reply"), "message" -> msgRefJs(message))
      case WhenStmtDto(condition, conditionId, negated, thenS, elseS) =>
        ujson.Obj.from(
          Seq[(String, ujson.Value)]("kind" -> ujson.Str("when"))
            ++ condition.map(x => "condition" -> (ujson.Str(x): ujson.Value))
            ++ conditionId.map(x => "conditionIdentifier" -> (ujson.Str(x): ujson.Value))
            ++ Seq[(String, ujson.Value)](
              "negated" -> ujson.Bool(negated),
              "then" -> stmtArr(thenS),
              "else" -> stmtArr(elseS)
            )
        )
      case MatchStmtDto(expression, cases, default) =>
        ujson.Obj(
          "kind" -> ujson.Str("match"),
          "expression" -> ujson.Str(expression),
          "cases" -> ujson.Arr.from(
            cases.map(c =>
              ujson.Obj("pattern" -> ujson.Str(c.pattern), "statements" -> stmtArr(c.statements))
            )
          ),
          "default" -> stmtArr(default)
        )
  end writeStatement

  // Given ReadWriters. These are lazy (Scala 3 parameterless givens), so the
  // mutual recursion FieldDto <-> TypeExprDto resolves correctly.
  // ujson <-> InteractionDto. Containers nest `interactions` recursively.

  private def refJs(r: RefDto): ujson.Value =
    ujson.Obj("kind" -> ujson.Str(r.kind), "path" -> ujson.Str(r.path))
  private def readRef(v: ujson.Value): RefDto = RefDto(v.obj("kind").str, v.obj("path").str)
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
      case "self"         => SelfIxnDto(readRef(m("from")), m("relationship").str)
      case "focusOnGroup" => FocusOnGroupIxnDto(m("user").str, m("group").str)
      case "directToURL"  => DirectToURLIxnDto(m("user").str, m("url").str)
      case "showOutput"   => ShowOutputIxnDto(m("output").str, m("relationship").str, m("user").str)
      case "selectInput"  => SelectInputIxnDto(m("user").str, m("input").str)
      case "takeInput"    => TakeInputIxnDto(m("user").str, m("input").str)
      case "parallel"     => ParallelIxnDto(readIxns(m.get("interactions")))
      case "sequential"   => SequentialIxnDto(readIxns(m.get("interactions")))
      case "optional"     => OptionalIxnDto(readIxns(m.get("interactions")))
      case other => throw new IllegalArgumentException(s"Unknown interaction kind: '$other'")
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
      case FocusOnGroupIxnDto(user, group) =>
        ujson.Obj(
          "kind" -> ujson.Str("focusOnGroup"),
          "user" -> ujson.Str(user),
          "group" -> ujson.Str(group)
        )
      case DirectToURLIxnDto(user, url) =>
        ujson.Obj(
          "kind" -> ujson.Str("directToURL"),
          "user" -> ujson.Str(user),
          "url" -> ujson.Str(url)
        )
      case ShowOutputIxnDto(output, rel, user) =>
        ujson.Obj(
          "kind" -> ujson.Str("showOutput"),
          "output" -> ujson.Str(output),
          "relationship" -> ujson.Str(rel),
          "user" -> ujson.Str(user)
        )
      case SelectInputIxnDto(user, input) =>
        ujson.Obj(
          "kind" -> ujson.Str("selectInput"),
          "user" -> ujson.Str(user),
          "input" -> ujson.Str(input)
        )
      case TakeInputIxnDto(user, input) =>
        ujson.Obj(
          "kind" -> ujson.Str("takeInput"),
          "user" -> ujson.Str(user),
          "input" -> ujson.Str(input)
        )
      case ParallelIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("parallel"), "interactions" -> ixnArr(ixns))
      case SequentialIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("sequential"), "interactions" -> ixnArr(ixns))
      case OptionalIxnDto(ixns) =>
        ujson.Obj("kind" -> ujson.Str("optional"), "interactions" -> ixnArr(ixns))
    end match
  end writeInteraction

  given typeExprRW: ReadWriter[TypeExprDto] =
    readwriter[ujson.Value].bimap[TypeExprDto](writeTypeExpr, readTypeExpr)
  given statementRW: ReadWriter[StatementDto] =
    readwriter[ujson.Value].bimap[StatementDto](writeStatement, readStatement)
  given interactionRW: ReadWriter[InteractionDto] =
    readwriter[ujson.Value].bimap[InteractionDto](writeInteraction, readInteraction)
  given userStoryRW: ReadWriter[UserStoryDto] = macroRW
  given useCaseRW: ReadWriter[UseCaseDto] = macroRW
  given epicRW: ReadWriter[EpicDto] = macroRW
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
  given fieldRW: ReadWriter[FieldDto] = macroRW
  given messageRefRW: ReadWriter[MessageRefDto] = macroRW
  given onClauseRW: ReadWriter[OnClauseDto] = macroRW
  given handlerRW: ReadWriter[HandlerDto] = macroRW
  given invariantRW: ReadWriter[InvariantDto] = macroRW
  given constantRW: ReadWriter[ConstantDto] = macroRW
  given userRW: ReadWriter[UserDto] = macroRW
  given stateRW: ReadWriter[StateDto] = macroRW
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

end JsonModel
