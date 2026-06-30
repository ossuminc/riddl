/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.json

/** The JSON wire schema for the RIDDL JSON input method.
  *
  * These DTO (data transfer object) case classes describe the JSON document an
  * external producer (e.g. an AI model) emits. They are decoupled from the
  * RIDDL [[com.ossuminc.riddl.language.AST]]: [[JsonAstBuilder]] maps a
  * [[JsonModel.RootDto]] onto an `AST.Root`, applying RIDDL's required defaults
  * so the result is correct-by-construction for the supported subset.
  *
  * Serialization uses upickle, which is cross-compiled for JVM, JS, and Native,
  * keeping the whole path Native-safe (no I/O, no JVM-only dependency).
  *
  * Phase 1 subset: domains, contexts, entities, types, fields, messages
  * (command/event/query/result), state (record reference), handlers with
  * on-clauses, invariants, authors, and the common type expressions. Later
  * phases extend the schema additively.
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

  /** Argument-less predefined kinds: UUID, Boolean, Date, TimeStamp, Integer,
    * Whole, Natural, Number, Real.
    */
  case class PredefDto(kind: String) extends TypeExprDto

  /** `{ "kind": "Decimal", "whole"?: Int, "fractional"?: Int }` */
  case class DecimalDto(whole: Option[Long] = None, fractional: Option[Long] = None) extends TypeExprDto

  /** `{ "kind": "Currency", "country"?: String }` */
  case class CurrencyDto(country: Option[String] = None) extends TypeExprDto

  /** `{ "kind": "Range", "min"?: Int, "max"?: Int }` */
  case class RangeDto(min: Option[Long] = None, max: Option[Long] = None) extends TypeExprDto

  /** `{ "kind": "Pattern", "pattern": ["regex", ...] }` — builder requires >=1. */
  case class PatternDto(pattern: Seq[String] = Nil) extends TypeExprDto

  /** `{ "kind": "Enum", "values": ["A", "B"] }` — builder requires >=1. */
  case class EnumDto(values: Seq[String] = Nil) extends TypeExprDto

  /** `{ "kind": "Alternation", "of": ["TypeA", "TypeB"] }` */
  case class AlternationDto(of: Seq[String] = Nil) extends TypeExprDto

  /** `{ "kind": "Record", "fields": [ <field> ] }` -> Aggregation. */
  case class RecordDto(fields: Seq[FieldDto] = Nil) extends TypeExprDto

  /** `{ "kind": "Alias", "ref": "SomeDeclaredType" }` */
  case class AliasDto(ref: String) extends TypeExprDto

  /** `{ "cardinality": "optional"|"zeroOrMore"|"oneOrMore", "of": <typeExpr> }` */
  case class CardinalityDto(cardinality: String, of: TypeExprDto) extends TypeExprDto

  // ---------------------------------------------------------------------------
  // Structural DTOs
  // ---------------------------------------------------------------------------

  case class RootDto(domains: Seq[DomainDto] = Nil)

  case class DomainDto(
    name: String,
    brief: Option[String] = None,
    authors: Seq[AuthorDto] = Nil,
    types: Seq[TypeDefDto] = Nil,
    contexts: Seq[ContextDto] = Nil
  )

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
    commands: Seq[MessageDto] = Nil,
    events: Seq[MessageDto] = Nil,
    queries: Seq[MessageDto] = Nil,
    results: Seq[MessageDto] = Nil,
    entities: Seq[EntityDto] = Nil,
    handlers: Seq[HandlerDto] = Nil
  )

  case class MessageDto(name: String, brief: Option[String] = None, fields: Seq[FieldDto] = Nil)

  case class EntityDto(
    name: String,
    brief: Option[String] = None,
    state: Option[StateDto] = None,
    types: Seq[TypeDefDto] = Nil,
    handlers: Seq[HandlerDto] = Nil,
    invariants: Seq[InvariantDto] = Nil
  )

  case class StateDto(name: String, recordType: String)

  case class HandlerDto(name: String, brief: Option[String] = None, onClauses: Seq[OnClauseDto] = Nil)

  /** `kind`: "message" | "init" | "other" | "term". For "message", `message`
    * carries the message ref + its kind. `statements` are v1 prompt/`do` texts.
    */
  case class OnClauseDto(
    kind: String,
    message: Option[MessageRefDto] = None,
    statements: Seq[String] = Nil
  )

  case class MessageRefDto(ref: String, kind: String)

  case class InvariantDto(name: String, condition: String, brief: Option[String] = None)

  case class FieldDto(name: String, `type`: TypeExprDto, brief: Option[String] = None)

  // ---------------------------------------------------------------------------
  // upickle wiring
  // ---------------------------------------------------------------------------

  /** Custom pickler that encodes `Option[T]` as null-or-value (the upickle
    * default encodes Option as a 0/1-element JSON array, which would force
    * callers to write `"brief": ["text"]`). Absent keys still fall back to the
    * DTO's default argument.
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

  /** ujson <-> TypeExprDto. Hand-written so the dual discriminator
    * (`cardinality` wrapper vs `kind` tag) and the defaults are explicit.
    */
  private def readTypeExpr(v: ujson.Value): TypeExprDto =
    val m = v.obj
    if m.contains("cardinality") then CardinalityDto(m("cardinality").str, readTypeExpr(m("of")))
    else
      m("kind").str match
        case "String" => StringDto(m.get("min").map(_.num.toLong), m.get("max").map(_.num.toLong))
        case "Id"     => IdDto(m.get("entity").map(_.str))
        case k @ ("UUID" | "Boolean" | "Date" | "TimeStamp" | "Integer" | "Whole" | "Natural" | "Number" |
            "Real") =>
          PredefDto(k)
        case "Decimal"  => DecimalDto(m.get("whole").map(_.num.toLong), m.get("fractional").map(_.num.toLong))
        case "Currency" => CurrencyDto(m.get("country").map(_.str))
        case "Range"    => RangeDto(m.get("min").map(_.num.toLong), m.get("max").map(_.num.toLong))
        case "Pattern"  => PatternDto(m.get("pattern").map(_.arr.map(_.str).toSeq).getOrElse(Nil))
        case "Enum"     => EnumDto(m.get("values").map(_.arr.map(_.str).toSeq).getOrElse(Nil))
        case "Alternation" => AlternationDto(m.get("of").map(_.arr.map(_.str).toSeq).getOrElse(Nil))
        case "Record" =>
          RecordDto(m.get("fields").map(_.arr.map(j => readJson[FieldDto](j)).toSeq).getOrElse(Nil))
        case "Alias" => AliasDto(m("ref").str)
        case other   => throw new IllegalArgumentException(s"Unknown type expression kind: '$other'")
    end if
  end readTypeExpr

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
      case EnumDto(vs) =>
        ujson.Obj("kind" -> ujson.Str("Enum"), "values" -> ujson.Arr.from(vs.map(ujson.Str(_))))
      case AlternationDto(of) =>
        ujson.Obj("kind" -> ujson.Str("Alternation"), "of" -> ujson.Arr.from(of.map(ujson.Str(_))))
      case RecordDto(fields) =>
        ujson.Obj("kind" -> ujson.Str("Record"), "fields" -> ujson.Arr.from(fields.map(f => writeJs(f))))
      case AliasDto(ref) => ujson.Obj("kind" -> ujson.Str("Alias"), "ref" -> ujson.Str(ref))
      case CardinalityDto(card, of) =>
        ujson.Obj("cardinality" -> ujson.Str(card), "of" -> writeTypeExpr(of))
  end writeTypeExpr

  // Given ReadWriters. These are lazy (Scala 3 parameterless givens), so the
  // mutual recursion FieldDto <-> TypeExprDto resolves correctly.
  given typeExprRW: ReadWriter[TypeExprDto] = readwriter[ujson.Value].bimap[TypeExprDto](writeTypeExpr, readTypeExpr)
  given fieldRW: ReadWriter[FieldDto] = macroRW
  given messageRefRW: ReadWriter[MessageRefDto] = macroRW
  given onClauseRW: ReadWriter[OnClauseDto] = macroRW
  given handlerRW: ReadWriter[HandlerDto] = macroRW
  given invariantRW: ReadWriter[InvariantDto] = macroRW
  given stateRW: ReadWriter[StateDto] = macroRW
  given messageRW: ReadWriter[MessageDto] = macroRW
  given typeDefRW: ReadWriter[TypeDefDto] = macroRW
  given entityRW: ReadWriter[EntityDto] = macroRW
  given contextRW: ReadWriter[ContextDto] = macroRW
  given authorRW: ReadWriter[AuthorDto] = macroRW
  given domainRW: ReadWriter[DomainDto] = macroRW
  given rootRW: ReadWriter[RootDto] = macroRW

  /** Parse a JSON string into the wire model. Throws on malformed JSON or an
    * unknown type-expression kind; [[com.ossuminc.riddl.RiddlLib.parseJson]]
    * catches and converts to a clean failure.
    */
  def readRoot(json: String): RootDto = readJson[RootDto](json)

end JsonModel
