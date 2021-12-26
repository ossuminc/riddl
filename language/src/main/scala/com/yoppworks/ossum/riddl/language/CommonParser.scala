package com.yoppworks.ossum.riddl.language

import fastparse.*
import ScalaWhitespace.*
import com.yoppworks.ossum.riddl.language.AST.*
import Terminals.Keywords
import Terminals.Operators
import Terminals.Punctuation
import Terminals.Readability

import scala.language.postfixOps

/** Common Parsing Rules */
trait CommonParser extends NoWhiteSpaceParsers {

  def include[K, u: P](parser: P[?] => P[Seq[K]]): P[Seq[K]] = {
    P(Keywords.include ~/ literalString).map { str => doInclude(str, Seq.empty[K])(parser) }
  }

  def importDef[u: P]: P[DomainDefinition] = {
    P(Keywords.import_ ~ location ~/ domainRef ~/ Readability.from ~ literalString).map { tuple =>
      doImport(tuple._1, tuple._2, tuple._3)
    }
  }

  def optionalNestedContent[u: P, T](parser: => P[T]): P[Seq[T]] = {
    P(open ~ parser.rep.? ~ close).map(_.getOrElse(Seq.empty[T]))
  }

  def undefined[u: P, RT](ret: RT): P[RT] = { P(Punctuation.undefined /).map(_ => ret) }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  def markdownLines[u: P]: P[Seq[LiteralString]] = { P(markdownLine.rep(1)) }

  def as[u: P]: P[Unit] = { P(Readability.as | Readability.by).? }

  def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~ (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~ close) |
        literalString.map(Seq(_))
    )
  }

  def description[u: P]: P[Option[Description]] = {
    P(location ~ (Keywords.described | Keywords.explained) ~ as ~ docBlock).?.map {
      case Some((loc, lines)) => Some(Description(loc, lines))
      case None               => None
    }
  }

  def literalInteger[u: P]: P[LiteralInteger] = {
    P(location ~ (Operators.plus | Operators.minus).? ~ CharIn("0-9").rep(1).!.map(_.toInt))
      .map(s => LiteralInteger(s._1, BigInt(s._2)))
  }

  def literalDecimal[u: P]: P[LiteralDecimal] = {
    P(
      location ~ (Operators.plus | Operators.minus).?.! ~ CharIn("0-9").rep(1).! ~
        (Punctuation.dot ~ CharIn("0-9").rep(0)).?.! ~
        ("E" ~ CharIn("+\\-") ~ CharIn("0-9").rep(min = 1, max = 3)).?.!
    ).map { case (loc, a, b, c, d) => LiteralDecimal(loc, BigDecimal(a + b + c + d)) }
  }

  def simpleIdentifier[u: P]: P[String] = {
    P((CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_").?).!)
  }

  def quotedIdentifier[u: P]: P[String] = {
    P("'" ~/ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~ "'")
  }

  def anyIdentifier[u: P]: P[String] = { P(simpleIdentifier | quotedIdentifier) }

  def identifier[u: P]: P[Identifier] = {
    P(location ~ anyIdentifier).map(tpl => (Identifier.apply _).tupled(tpl))
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    P(location ~ anyIdentifier.repX(1, Punctuation.dot).map(_.reverse))
      .map(tpl => (PathIdentifier.apply _).tupled(tpl))
  }

  def is[u: P]: P[Unit] = {
    P(Readability.is | Readability.are | Punctuation.colon | Punctuation.equals)./
  }

  def open[u: P]: P[Unit] = { P(Punctuation.curlyOpen) }

  def close[u: P]: P[Unit] = { P(Punctuation.curlyClose) }

  def options[u: P, TY <: RiddlValue](
    validOptions: => P[String]
  )(mapper: => (Location, String) => TY
  ): P[Seq[TY]] = {
    P(
      (Keywords.options ~/ Punctuation.roundOpen ~ (location ~ validOptions).rep(1)
        .map(_.map(mapper.tupled(_))) ~ Punctuation.roundClose) |
        (Keywords.option ~ is ~/ (location ~ validOptions).map(tpl => Seq(mapper.tupled(tpl))))
    ).?.map {
      case Some(x) => x
      case None    => Seq.empty[TY]
    }
  }

  def mapTo[T <: Definition](seq: Option[Seq[Definition]]): Seq[T] = {
    seq.map(_.map(_.asInstanceOf[T])).getOrElse(Seq.empty[T])
  }

  def commandRef[u: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~/ pathIdentifier).map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[u: P]: P[EventRef] = {
    P(location ~ Keywords.event ~/ pathIdentifier).map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[u: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~/ pathIdentifier).map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[u: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~/ pathIdentifier).map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def messageRef[u: P]: P[MessageReference] = { P(commandRef | eventRef | queryRef | resultRef) }

  def entityRef[u: P]: P[EntityRef] = {
    P(location ~ Keywords.entity ~/ pathIdentifier).map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def topicRef[u: P]: P[TopicRef] = {
    P(location ~ Keywords.topic ~/ pathIdentifier).map(tpl => (TopicRef.apply _).tupled(tpl))
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(location ~ pathIdentifier).map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def actionRef[u: P]: P[FunctionRef] = {
    P(location ~ Keywords.action ~/ pathIdentifier).map(tpl => (FunctionRef.apply _).tupled(tpl))
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~/ pathIdentifier).map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[u: P]: P[DomainRef] = {
    P(location ~ Keywords.domain ~/ pathIdentifier).map(tpl => (DomainRef.apply _).tupled(tpl))
  }
}
