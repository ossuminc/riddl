package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.AST._

object Parser {
  import fastparse._
  import ScriptWhitespace._

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
      ) /
    ).!.map {
      case "Boolean" ⇒ AST.Boolean
      case "String" ⇒ AST.String
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
    P("select" ~/ identifier.map(NamedType).rep(2, P("|"))).map { types ⇒
      Alternation(types)
    }
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P("combine" ~/ P(identifier ~ ":" ~ typeExpression).rep(1, P(",")))
      .map(types ⇒ Aggregation(types.toMap[String, Type]))
  }

  def cardinality[_: P](p: ⇒ P[Type]): P[Type] = {
    P(p ~ ("?".! | "*".! | "+".! | "")).map {
      case (typ, "?") ⇒ Optional(typ)
      case (typ, "+") ⇒ OneOrMore(typ)
      case (typ, "*") ⇒ ZeroOrMore(typ)
      case (typ, _) ⇒ typ
    }
  }

  def typeExpression[_: P]: P[Type] = {
    P(
      cardinality(
        literalTypeExpression | enumerationType | alternationType |
          aggregationType | identifier.map(NamedType)
      )
    )
  }

  def typeDef[_: P]: P[TypeDef] = {
    P("type" ~/ identifier ~ "=" ~ typeExpression /).map {
      case (i, ty) ⇒ TypeDef(i, ty)
    }
  }

  def commandDef[_: P]: P[CommandDef] = {
    P("command" ~/ identifier ~ "=" ~ typeExpression /).map {
      case (id, expr) =>
        CommandDef(id, expr)
    }
  }

  def eventDef[_: P]: P[EventDef] = {
    P("event" ~/ identifier ~ "=" ~ typeExpression ~ "from" ~ identifier).map {
      case (name, typ, cmd) =>
        EventDef(name, typ, cmd)
    }
  }

  def queryDef[_: P]: P[CommandDef] = {
    P("query" ~/ identifier ~ "=" ~ typeExpression /).map {
      case (id, expr) =>
        CommandDef(id, expr)
    }
  }

  def resultDef[_: P]: P[EventDef] = {
    P("result" ~/ identifier ~ "=" ~ typeExpression ~ "for" ~ identifier).map {
      case (name, typ, cmd) =>
        EventDef(name, typ, cmd)
    }
  }

  def contextDefinitions[_: P]: P[Def] = {
    P(typeDef | commandDef | eventDef | queryDef | resultDef)
  }

  def contextDef[_: P]: P[ContextDef] = {
    P("context" ~/ identifier ~ "{" ~ contextDefinitions.rep(0) ~ "}").map {
      case (name, defs) => ContextDef(name, defs)
    }
  }

  def domainPath[_: P]: P[DomainPath] = {
    P(("." ~ identifier).rep(0) ~ identifier).map {
      case (paths, name) => DomainPath(paths, name)
    }
  }

  def domainDefinitions[_: P]: P[Def] = {
    P(typeDef | contextDef)
  }

  def domainDef[_: P]: P[DomainDef] = {
    P("domain " ~ domainPath ~ "{" ~ domainDefinitions.rep(0) ~ "}").map {
      case (path, defs) =>
        DomainDef(path, defs)
    }
  }

  def parse[_: P]: P[Seq[DomainDef]] = {
    P(domainDef.rep(0))
  }
}
