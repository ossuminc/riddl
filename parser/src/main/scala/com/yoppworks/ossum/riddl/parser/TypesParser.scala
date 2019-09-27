package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._
import CommonParser._

/** Unit Tests For TypesParser */
object TypesParser {

  def literalTypeExpression[_: P]: P[Type] = {
    P(
      StringIn(
        "String",
        "Number",
        "Boolean",
        "Id",
        "Date",
        "Time",
        "TimeStamp",
        "URL"
      )./
    ).!.map {
      case "Boolean" ⇒ AST.Boolean
      case "String" ⇒ AST.Strng
      case "Number" ⇒ AST.Number
      case "Id" ⇒ AST.Id
      case "Date" ⇒ AST.Date
      case "Time" ⇒ AST.Time
      case "TimeStamp" ⇒ AST.TimeStamp
      case "URL" ⇒ AST.URL
    }
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P("any" ~/ "[" ~ identifier.rep ~ "]").map { enums ⇒
      Enumeration(enums)
    }
  }

  def alternationType[_: P]: P[Alternation] = {
    P(
      "choose" ~/ identifier.rep(2, P("or"))
    ).map(_.map(TypeRef)).map(Alternation)
  }

  def typeExpression[_: P]: P[Type] = {
    P(cardinality(typeRef | identifier.map(TypeRef)))
  }

  def cardinality[_: P](p: ⇒ P[TypeRef]): P[Type] = {
    P(p ~ ("?".! | "*".! | "+".!).?).map {
      case (typ, Some("?")) ⇒ Optional(typ)
      case (typ, Some("+")) ⇒ OneOrMore(typ)
      case (typ, Some("*")) ⇒ ZeroOrMore(typ)
      case (typ, Some(_)) => typ
      case (typ, None) ⇒ typ
    }
  }

  def field[_: P]: P[(Identifier, Type)] = {
    P(identifier ~ ":" ~ typeExpression)
  }

  def fields[_: P]: P[Seq[(Identifier, Type)]] = {
    P(field.rep(1, P(",")))
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P(
      "combine" ~/ "{" ~ fields ~ "}"
    ).map(types ⇒ Aggregation(types.toMap[Identifier, Type]))
  }

  def typeDefKinds[_: P]: P[Type] = {
    P(
      enumerationType | alternationType | aggregationType | typeExpression
    )
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(
      "type" ~ Index ~/ identifier ~ "=" ~ typeDefKinds
    ).map {
      case (index, i, ty) ⇒ TypeDef(index, i, ty)
    }
  }

}
