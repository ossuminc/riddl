package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.Node._

object Parser {
  import fastparse._
  import ScriptWhitespace._

  def eq[_: P]: P[Terminal] = {
    P("=").map(_ ⇒ Node.eq)
  }

  def `type`[_: P]: P[Terminal] = {
    P("type").map(_ ⇒ Node.`type`)
  }

  def literalString[_: P]: P[String] = {
    P("\"" ~~/ CharsWhile(_ != '"', 0).! ~~ "\"").!
  }

  def literalInt[_: P]: P[Int] = {
    P(CharIn("0-9").rep(1).!.map(_.toInt))
  }

  def identifier[_: P]: P[String] = {
    P(CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_$%@!", 1)).!
  }

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
      )
    ).!.map {
      case "Boolean" ⇒ Node.Boolean
      case "String" ⇒ Node.String
      case "Number" ⇒ Node.Number
      case "Id" ⇒ Node.Id
      case "Date" ⇒ Node.Date
      case "Time" ⇒ Node.Time
      case "TimeStamp" ⇒ Node.TimeStamp
      case "URL" ⇒ Node.URL
    }
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P("any" ~/ "[" ~ identifier.rep ~ "]").map(enums ⇒ Enumeration(enums))
  }

  def alternationType[_: P]: P[Alternation] = {
    P("select" ~/ (identifier).rep(2, P("|"))).map(types ⇒ Alternation(types))
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P("combine" ~/ P(identifier ~ ":" ~ identifier).rep(2, P(",")))
      .map(types ⇒ Aggregation(types.toMap[String, String]))
  }

  def optionalType[_: P]: P[Optional] = {
    P(typeExpression ~ "?").map(typ ⇒ Optional(typ))
  }

  def zeroOrMore[_: P]: P[ZeroOrMore] = {
    P(typeExpression ~ "*").map(typ ⇒ ZeroOrMore(typ))
  }

  def oneOrMore[_: P]: P[OneOrMore] = {
    P(typeExpression ~ "+").map(typ ⇒ OneOrMore(typ))
  }

  def tupleType[_: P]: P[Tuple] = {
    P("(" ~ typeExpression.rep(2, P(",")) ~ ")").map(types ⇒ Tuple(types))
  }

  def typeExpression[_: P]: P[Type] = {
    P(
      literalTypeExpression | enumerationType | alternationType |
        aggregationType | optionalType | zeroOrMore | oneOrMore | tupleType
    )
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(`type` ~ identifier ~ eq ~ typeExpression).map {
      case (_, i, _, ty) ⇒ TypeDef(i, ty)
    }
  }

  def parse[_: P]: P[Seq[Def]] = {
    P("{" ~ typeDef.rep(0) ~ "}")
  }
}
