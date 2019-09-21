package com.yoppworks.ossum.idddl.parser

import java.io.File

import AST._
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.io.Source

object Parser {
  import fastparse._
  import ScalaWhitespace._
  import AST._

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

  def typeRef[_: P]: P[TypeRef] = {
    P("type" ~/ identifier).map(TypeRef)
  }

  def enumerationType[_: P]: P[Enumeration] = {
    P("any" ~/ "[" ~ identifier.rep ~ "]").map { enums ⇒
      Enumeration(enums)
    }
  }

  def alternationType[_: P]: P[Alternation] = {
    P("select" ~/ identifier.rep(2, P("|")))
      .map(_.map(TypeRef))
      .map(Alternation)
  }

  def typeExpression[_: P]: P[Type] = {
    P(cardinality(literalTypeExpression | typeRef))
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
      "command" ~ Index ~/ identifier ~ "=" ~ typeDefKinds ~ "yields" ~
        eventRefs
    ).map { tpl ⇒
      (CommandDef.apply _).tupled(tpl)
    }
  }

  def eventRefs[_: P]: P[EventRefs] = {
    P("event" ~~ "s".? ~/ identifier.rep(1, P(","))).map(_.map(EventRef))
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      "event" ~ Index ~/ identifier ~ "=" ~ typeDefKinds
    ).map { tpl ⇒
      (EventDef.apply _).tupled(tpl)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      "query" ~ Index ~/ identifier ~ "=" ~ typeDefKinds ~ "yields" ~
        resultRefs
    ).map { tpl ⇒
      (QueryDef.apply _).tupled(tpl)
    }
  }

  def resultRefs[_: P]: P[ResultRefs] = {
    P("result" ~~ "s".? ~/ identifier.rep(1, P(","))).map(_.map(ResultRef))
  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      "result" ~ Index ~/ identifier ~ "=" ~ typeDefKinds
    ).map { tpl ⇒
      (ResultDef.apply _).tupled(tpl)
    }
  }

  def entityOption[_: P]: P[EntityOption] = {
    P(StringIn("aggregate", "persistent", "consistent", "available")).!.map {
      case "aggregate"  => EntityAggregate
      case "persistent" => EntityPersistent
      case "consistent" => EntityConsistent
      case "available"  => EntityAvailable
    }
  }

  def channelRef[_: P]: P[ChannelRef] = {
    P("channel" ~/ identifier).map(ChannelRef)
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      entityOption.rep(0) ~ "entity" ~ Index ~/ identifier ~ "=" ~
        typeDefKinds ~
        ("consumes" ~ channelRef).? ~
        ("produces" ~/ channelRef).?
    ).map { tpl ⇒
      (EntityDef.apply _).tupled(tpl)
    }
  }

  def domainRef[_: P]: P[DomainRef] = {
    P(
      "domain" ~/ pathIdentifier
    ).map(names ⇒ DomainRef(names.mkString(".")))
  }

  def contextRef[_: P]: P[ContextRef] = {
    P("context" ~/ identifier).map(ContextRef)
  }

  def adaptorDef[_: P]: P[AdaptorDef] = {
    P(
      "adaptor" ~/ Index ~ identifier ~ "for" ~/ domainRef.? ~/ contextRef
    ).map { tpl =>
      (AdaptorDef.apply _).tupled(tpl)
    }
  }

  def contextOptions[_: P]: P[ContextOption] = {
    P(StringIn("device", "service", "function")).!.map {
      case "device" ⇒ DeviceOption
      case "service" ⇒ ServiceOption
      case "function" ⇒ FunctionOption
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      contextOptions.rep(0) ~ "context" ~ Index ~/ identifier ~ "{" ~
        typeDef.rep(0) ~ commandDef.rep(0) ~ eventDef.rep(0) ~
        queryDef.rep(0) ~ resultDef.rep(0) ~
        entityDef.rep(0) ~ adaptorDef.rep(0) ~ "}"
    ).map { args =>
      (ContextDef.apply _).tupled(args)
    }
  }

  def channelDef[_: P]: P[ChannelDef] = {
    P("channel" ~ Index ~/ identifier).map {
      case (index, name) => ChannelDef(index, name)
    }
  }

  def domainDefinitions[_: P]: P[Def] = {
    P(typeDef | contextDef)
  }

  def domainDef[_: P]: P[DomainDef] = {
    P(
      "domain" ~ Index ~/ pathIdentifier ~ "{" ~/
        channelDef.rep(0) ~
        contextDef.rep(0) ~ "}"
    ).map {
      case (index, path, channels, contexts) =>
        DomainDef(index, path.dropRight(1), path.last, channels, contexts)
    }
  }

  def parse[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ domainDef.rep(0) ~ End)
  }

  def parseString(input: String): Either[String, Seq[DomainDef]] = {
    fastparse.parse(input, parse(_)) match {
      case Success(content, _) ⇒
        Right(content)
      case failure @ Failure(_, index, _) ⇒
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val trace = failure.trace()
        Left(s"""Parse of '$annotated_input' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }

  def parseFile(file: File): Either[String, Seq[DomainDef]] = {
    val source = Source.fromFile(file)
    parseSource(source, file.getPath)
  }

  def parseSource(
    source: Source,
    name: String
  ): Either[String, Seq[DomainDef]] = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    fastparse.parse(input, parse(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(label, index, _) ⇒
        val where = s"at $name:$index"
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val trace = failure.trace()
        Left(s"""Parse of '$annotated_input' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }
}
