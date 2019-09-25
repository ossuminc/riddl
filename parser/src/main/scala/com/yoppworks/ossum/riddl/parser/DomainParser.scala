package com.yoppworks.ossum.riddl.parser

import java.io.File

import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.io.Source
import AST._
import fastparse._
import ScalaWhitespace._
import CommonParser._
import TypesParser._
import EntityParser._
import InteractionParser._

object DomainParser {

  def adaptorDef[_: P]: P[AdaptorDef] = {
    P(
      "adaptor" ~/ Index ~ identifier ~ "for" ~/ domainRef.? ~/ contextRef
    ).map { tpl =>
      (AdaptorDef.apply _).tupled(tpl)
    }
  }

  def contextOptions[_: P]: P[ContextOption] = {
    P(StringIn("wrapper", "function", "gateway")).!.map {
      case "wrapper" ⇒ WrapperOption
      case "function" ⇒ FunctionOption
      case "gateway" ⇒ GatewayOption
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      contextOptions.rep(0) ~ "context" ~ Index ~/ identifier ~ "{" ~
        typeDef.rep(0) ~ commandDef.rep(0) ~ eventDef.rep(0) ~
        queryDef.rep(0) ~ resultDef.rep(0) ~
        entityDef.rep(0) ~ adaptorDef.rep(0) ~ interactionDef.rep(0) ~
        "}"
    ).map { args =>
      (ContextDef.apply _).tupled(args)
    }
  }

  def channelDef[_: P]: P[ChannelDef] = {
    P("channel" ~ Index ~/ identifier).map {
      case (index, name) => ChannelDef(index, name)
    }
  }

  def domainDef[_: P]: P[DomainDef] = {
    P(
      "domain" ~ Index ~/ identifier ~
        ("is" ~ "subdomain" ~ "of" ~/ identifier).? ~ "{" ~/
        typeDef.rep(0) ~
        channelDef.rep(0) ~
        interactionDef.rep(0) ~
        contextDef.rep(0) ~
        "}"./
    ).map { tpl ⇒
      (DomainDef.apply _).tupled(tpl)
    }
  }

  def topLevelDomains[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ P(domainDef).rep(0) ~ End)
  }

  def annotated_input(input: String, index: Int): String = {
    input.substring(0, index) + "^" + input.substring(index)
  }

  def parseString[T](
    input: String,
    parser: P[_] ⇒ P[T]
  ): Either[String, T] = {
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) ⇒
        Right(content)
      case failure @ Failure(_, index, _) ⇒
        val marked_up = annotated_input(input, index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }

  def parseFile[T](file: File, parser: P[_] ⇒ P[T]): Either[String, T] = {
    val source = Source.fromFile(file)
    parseSource(source, file.getPath, parser)
  }

  def parseSource[T](
    source: Source,
    name: String,
    parser: P[_] => P[T]
  ): Either[String, T] = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(label, index, _) ⇒
        val where = s"at $name:$index"
        val marked_up = annotated_input(input, index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $where"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }
}
