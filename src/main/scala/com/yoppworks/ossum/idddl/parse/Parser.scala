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
    P(CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_", 3)).!
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
    ).!.map { value: String ⇒
      value match {
        case "Boolean" ⇒ Node.Boolean
        case "String" ⇒ Node.String
        case "Number" ⇒ Node.Number
        case "Id" ⇒ Node.Id
        case "Date" ⇒ Node.Date
        case "Time" ⇒ Node.Time
        case "TimeStamp" ⇒ Node.TimeStamp
        case "URL" ⇒ Node.URL
        case _ ⇒ TypeError(s"unknown literal type `$value`")
      }
    }
  }

  def typeExpression[_: P]: P[Type] = {
    P(literalTypeExpression)
  }

  def parseTypeDef[_: P]: P[(String, Type)] = {
    P(`type` ~ identifier ~ eq ~ typeExpression).map {
      case (_, i, _, ty) ⇒ (i → ty)
    }
  }
  def parse[_: P]: P[Unit] = { P("a") }

}
