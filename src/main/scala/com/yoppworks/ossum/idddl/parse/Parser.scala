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

  def simpleIdentifier[_: P]: P[String] = {
    P((CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_").?).!)
  }

  def identifier[_: P]: P[String] = {
    P(
      simpleIdentifier |
        ("'" ~/ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~ "'")
    )
  }

  def pathIdentifier[_: P]: P[Seq[String]] = {
    P(identifier.repX(1, P(".")))
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
      )./
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

  def namedType[_: P]: P[NamedType] = {
    P(identifier.map(NamedType))
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P("any" ~/ "[" ~ identifier.rep ~ "]").map { enums ⇒
      Enumeration(enums)
    }
  }

  def alternationType[_: P]: P[Alternation] = {
    P("select" ~/ namedType.rep(2, P("|"))).map { types ⇒
      Alternation(types)
    }
  }

  def typeExpression[_: P]: P[Type] = {
    P(cardinality(literalTypeExpression | namedType))
  }

  def cardinality[_: P](p: ⇒ P[Type]): P[Type] = {
    P(p ~ ("?".! | "*".! | "+".!).?).map {
      case (typ, Some("?")) ⇒ Optional(typ)
      case (typ, Some("+")) ⇒ OneOrMore(typ)
      case (typ, Some("*")) ⇒ ZeroOrMore(typ)
      case (typ, Some(_)) => typ
      case (typ, None) ⇒ typ
    }
  }

  def field[_: P]: P[(String, Type)] = {
    P(identifier ~ ":" ~ typeExpression)
  }

  def fields[_: P]: P[Seq[(String, Type)]] = {
    P(field.rep(1, P(",")))
  }

  def aggregationType[_: P]: P[Aggregation] = {
    P(
      "combine" ~/ "{" ~ fields ~ "}"
    ).map(types ⇒ Aggregation(types.toMap[String, Type]))
  }

  def typeDefKinds[_: P]: P[Type] = {
    P(
      enumerationType | alternationType | aggregationType | typeExpression
    )
  }

  def typeDef[_: P]: P[TypeDef] = {
    P(
      "type" ~/ identifier ~ "=" ~ typeDefKinds
    ).map {
      case (i, ty) ⇒ TypeDef(i, ty)
    }
  }

  def commandDef[_: P]: P[CommandDef] = {
    P(
      "command" ~/ identifier ~ ":" ~ namedType ~ "yields" ~
        identifier.rep(1, P(","))
    ).map {
      case (id, expr, yields) =>
        CommandDef(id, expr, yields)
    }
  }

  def eventDef[_: P]: P[EventDef] = {
    P("event" ~/ identifier ~ ":" ~ typeExpression).map {
      case (name, typ) =>
        EventDef(name, typ)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      "query" ~/ identifier ~ ":" ~ typeExpression ~ "yields" ~ identifier
        .rep(1, P(","))
    ).map {
      case (id, expr, results) =>
        QueryDef(id, expr, results)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P("result" ~/ identifier ~ ":" ~ typeExpression).map {
      case (name, typ) =>
        ResultDef(name, typ)
    }
  }

  def entityOptions[_: P]: P[EntityOption] = {
    P(
      "aggregate".!.map(_ => EntityAggregate) |
        "persistent".!.map(_ => EntityPersistent)
    )
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      entityOptions.rep(0) ~ "entity" ~/ identifier ~ ":" ~ typeExpression ~
        ("consumes" ~/ "[" ~ identifier.rep(1, ",") ~ "]").? ~
        ("produces" ~/ "[" ~ identifier.rep(1, ",") ~ "]").?
    ).map {
      case (options, name, typ, consumes, produces) =>
        EntityDef(
          name,
          options,
          typ,
          consumes.getOrElse(Seq.empty[String]),
          produces.getOrElse(Seq.empty[String])
        )
    }
  }

  def contextDefinitions[_: P]: P[Def] = {
    P(
      typeDef | commandDef | eventDef | queryDef | resultDef | entityDef
    )
  }

  def contextDef[_: P]: P[ContextDef] = {
    P("context" ~/ identifier ~ "{" ~ contextDefinitions.rep(0) ~ "}").map {
      case (name, defs) => ContextDef(name, defs)
    }
  }

  def domainPath[_: P]: P[DomainPath] = {
    P(pathIdentifier).map(DomainPath)
  }

  def domainDefinitions[_: P]: P[Def] = {
    P(typeDef | contextDef)
  }

  def domainDef[_: P]: P[DomainDef] = {
    P("domain " ~/ domainPath ~ "{" ~/ contextDef.rep(0) ~ "}").map {
      case (path, defs) =>
        DomainDef(path, defs)
    }
  }

  def parse[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ domainDef.rep(0) ~ End)
  }
}
