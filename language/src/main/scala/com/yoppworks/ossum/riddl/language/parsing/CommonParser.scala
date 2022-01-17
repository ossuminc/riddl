package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Operators, Punctuation, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.language.postfixOps

/** Common Parsing Rules */
trait CommonParser extends NoWhiteSpaceParsers {

  def include[K, u: P](parser: P[?] => P[Seq[K]]): P[Seq[K]] = {
    P(Keywords.include ~/ literalString).map { str => doInclude(str, Seq.empty[K])(parser) }
  }

  def importDef[u: P]: P[DomainDefinition] = {
    P(location ~ Keywords.import_ ~ Keywords.domain ~ identifier ~ Readability.from ~ literalString)
      .map { tuple => doImport(tuple._1, tuple._2, tuple._3) }
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
      (Keywords.options ~/ Punctuation.roundOpen ~ location ~ validOptions ~
        (location ~ Punctuation.comma ~ validOptions).rep(0) ~ Punctuation.roundClose)
        .map { case (headLoc, headOption, tail) =>
          val end: Seq[TY] = tail.map(i => mapper(i._1, i._2))
          mapper(headLoc, headOption) +: end
        } | (Keywords.option ~/ Readability.is ~ location ~ validOptions)
        .map(tpl => Seq(mapper.tupled(tpl)))
    ).?.map {
      case Some(seq) => seq
      case None => Seq.empty[TY]
    }
  }

  def mapTo[T <: Definition](seq: Option[Seq[Definition]]): Seq[T] = {
    seq.map(_.map(_.asInstanceOf[T])).getOrElse(Seq.empty[T])
  }

}
