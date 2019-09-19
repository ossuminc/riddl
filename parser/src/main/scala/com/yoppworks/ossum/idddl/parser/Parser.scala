package com.yoppworks.ossum.idddl.parser

import java.io.File

import AST._
import fastparse.Parsed.{Failure, Success}

import scala.io.Source

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
      "type" ~ Index ~/ identifier ~ "=" ~ typeDefKinds
    ).map {
      case (index, i, ty) ⇒ TypeDef(index, i, ty)
    }
  }

  def commandDef[_: P]: P[CommandDef] = {
    P(
      "command" ~ Index ~/ identifier ~ ":" ~ namedType ~ "yields" ~
        identifier.rep(1, P(","))
    ).map {
      case (index, id, expr, yields) =>
        CommandDef(index, id, expr, yields)
    }
  }

  def eventDef[_: P]: P[EventDef] = {
    P("event" ~ Index ~/ identifier ~ ":" ~ typeExpression).map {
      case (index, name, typ) =>
        EventDef(index, name, typ)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      "query" ~ Index ~/ identifier ~ ":" ~ typeExpression ~ "yields" ~
        identifier
        .rep(1, P(","))
    ).map {
      case (index, id, expr, results) =>
        QueryDef(index, id, expr, results)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P("result" ~ Index ~/ identifier ~ ":" ~ typeExpression).map {
      case (index, name, typ) =>
        ResultDef(index, name, typ)
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
      entityOptions.rep(0) ~ "entity" ~ Index ~/ identifier ~ ":" ~
        typeExpression ~
        ("consumes" ~/ "[" ~ identifier.rep(1, ",") ~ "]").? ~
        ("produces" ~/ "[" ~ identifier.rep(1, ",") ~ "]").?
    ).map {
      case (options, index, name, typ, consumes, produces) =>
        EntityDef(
          index,
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
    P("context" ~ Index ~/ identifier ~ "{" ~ contextDefinitions.rep(0) ~
      "}").map {
      case (index, name, defs) => ContextDef(index, name, defs)
    }
  }

  def domainPath[_: P]: P[DomainPath] = {
    P(pathIdentifier).map(DomainPath)
  }

  def domainDefinitions[_: P]: P[Def] = {
    P(typeDef | contextDef)
  }

  def domainDef[_: P]: P[DomainDef] = {
    P("domain" ~ Index ~/ domainPath ~ "{" ~/ contextDef.rep(0) ~ "}").map {
      case (index, path, defs) =>
        DomainDef(index, path, defs)
    }
  }

  def parse[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ domainDef.rep(0) ~ End)
  }

  def parseString(input: String): Either[String,Seq[DomainDef]] = {
    fastparse.parse(input, parse(_)) match {
      case Success(content, _) ⇒
        Right(content)
      case failure @ Failure(_, index, extra) ⇒
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val trace = failure.trace()
        Left(s"""Parse of '$annotated_input' failed at position $index"
           |${trace.longAggregateMsg}
           |""".stripMargin
        )
    }
  }

  def parseFile(file: File): Either[String,Seq[DomainDef]] = {
    val source = Source.fromFile(file)
    parseSource(source, file.getPath)
  }

  def parseSource(source: Source, name: String): Either[String, Seq[DomainDef]]
  = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    fastparse.parse(input, parse(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(label, index, extra) ⇒
        val where = s"at $name:$index"
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val trace = failure.trace()
        Left(s"""Parse of '$annotated_input' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin
        )
    }
  }
}
