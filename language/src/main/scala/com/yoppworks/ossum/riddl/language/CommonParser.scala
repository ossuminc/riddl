package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.AST._
import Terminals.Keywords
import Terminals.Operators
import Terminals.Punctuation
import Terminals.Readability

import scala.language.postfixOps

/** Common Parsing Rules */
trait CommonParser extends NoWhiteSpaceParsers {

  def include[K, _: P](parser: P[_] => P[Seq[K]]): P[Seq[K]] = {
    P(Keywords.include ~/ literalString).map { str => doInclude(str, Seq.empty[K])(parser) }
  }

  def importDef[_: P]: P[DomainDefinition] = {
    P(Keywords.import_ ~ location ~/ domainRef ~/ Readability.from ~ literalString).map { tuple =>
      doImport(tuple._1, tuple._2, tuple._3)
    }
  }

  def undefined[_: P]: P[Unit] = { P(Punctuation.undefined /) }

  def literalStrings[_: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  def markdownLines[_: P]: P[Seq[LiteralString]] = { P(markdownLine.rep(1)) }

  def docBlock[_: P]: P[Seq[LiteralString]] = {
    P((open ~ (markdownLines | literalStrings) ~ close) | literalString.map(Seq(_)))
  }

  def optionalNestedContent[_: P, T](parser: => P[T]): P[Seq[T]] = {
    P(open ~ parser.rep.? ~ close).map(_.getOrElse(Seq.empty[T]))
  }

  def brief[_: P]: P[Seq[LiteralString]] = {
    (Keywords.brief ~/ (literalString.map(Seq(_)) | docBlock)).?
      .map(_.getOrElse(Seq.empty[LiteralString]))
  }

  def details[_: P]: P[Seq[LiteralString]] = {
    (Keywords.details ~/ (literalString.map(Seq(_)) | docBlock)).?
      .map(_.getOrElse(Seq.empty[LiteralString]))
  }

  type ItemDictionary = Map[Identifier, Seq[LiteralString]]
  def items[_: P]: P[(Option[LiteralString], ItemDictionary)] = {
    P(
      Keywords.items ~/ (Punctuation.roundOpen ~ literalString ~ Punctuation.roundClose).? ~/ open ~
        (identifier ~ is ~ docBlock).rep.map(_.toMap) ~ close
    ).?.map(_.getOrElse(None -> Map.empty[Identifier, Seq[LiteralString]]))
  }

  def citations[_: P]: P[Seq[LiteralString]] = {
    P(Keywords.see ~/ docBlock).?.map(_.getOrElse(Seq.empty[LiteralString]))
  }

  def as[_: P]: P[Unit] = { P(Readability.as | Readability.by).? }

  case class DescriptionParts(
    brief: Seq[LiteralString],
    details: Seq[LiteralString],
    items: (Option[LiteralString], Map[Identifier, Seq[LiteralString]]),
    cites: Seq[LiteralString])

  def detailedDescription[_: P]: P[DescriptionParts] = {
    P(brief ~ details ~ items ~ citations).map(tpl => (DescriptionParts.apply _).tupled(tpl))
  }

  def literalStringsDescription[_: P]: P[DescriptionParts] = {
    literalStrings.map { strings =>
      DescriptionParts(
        strings,
        Seq.empty[LiteralString],
        None -> Map.empty[Identifier, Seq[LiteralString]],
        Seq.empty[LiteralString]
      )
    }
  }

  def docBlockDescription[_: P]: P[DescriptionParts] = {
    markdownLine.rep(1).map { block =>
      DescriptionParts(
        Seq.empty[LiteralString],
        block,
        None -> Map.empty[Identifier, Seq[LiteralString]],
        Seq.empty[LiteralString]
      )
    }
  }

  def description[_: P]: P[Option[Description]] = {
    P(
      location ~ (Keywords.described | Keywords.explained) ~ as ~ open ~/
        (literalStringsDescription | docBlockDescription | detailedDescription) ~ close
    ).?.map {
      case yes @ Some((loc, DescriptionParts(brief, details, (itemsName, items), cites))) =>
        Some(Description(loc, brief, details, itemsName, items, cites))
      case no @ None => None
    }
  }

  def literalInteger[_: P]: P[LiteralInteger] = {
    P(location ~ (Operators.plus | Operators.minus).? ~ CharIn("0-9").rep(1).!.map(_.toInt))
      .map(s => LiteralInteger(s._1, BigInt(s._2)))
  }

  def literalDecimal[_: P]: P[LiteralDecimal] = {
    P(
      location ~ (Operators.plus | Operators.minus).?.! ~ CharIn("0-9").rep(1).! ~
        (Punctuation.dot ~ CharIn("0-9").rep(0)).?.! ~
        ("E" ~ CharIn("+\\-") ~ CharIn("0-9").rep(min = 1, max = 3)).?.!
    ).map { case (loc, a, b, c, d) => LiteralDecimal(loc, BigDecimal(a + b + c + d)) }
  }

  def simpleIdentifier[_: P]: P[String] = {
    P((CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_").?).!)
  }

  def quotedIdentifier[_: P]: P[String] = {
    P("'" ~/ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~ "'")
  }

  def anyIdentifier[_: P]: P[String] = { P(simpleIdentifier | quotedIdentifier) }

  def identifier[_: P]: P[Identifier] = {
    P(location ~ anyIdentifier).map(tpl => (Identifier.apply _).tupled(tpl))
  }

  def pathIdentifier[_: P]: P[PathIdentifier] = {
    P(location ~ anyIdentifier.repX(1, Punctuation.dot).map(_.reverse))
      .map(tpl => (PathIdentifier.apply _).tupled(tpl))
  }

  def is[_: P]: P[Unit] = {
    P(Readability.is | Readability.are | Punctuation.colon | Punctuation.equals)./
  }

  def open[_: P]: P[Unit] = { P(Punctuation.curlyOpen) }

  def close[_: P]: P[Unit] = { P(Punctuation.curlyClose) }

  def options[_: P, TY <: RiddlValue](
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

  def commandRef[_: P]: P[CommandRef] = {
    P(location ~ Keywords.command ~/ pathIdentifier).map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[_: P]: P[EventRef] = {
    P(location ~ Keywords.event ~/ pathIdentifier).map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[_: P]: P[QueryRef] = {
    P(location ~ Keywords.query ~/ pathIdentifier).map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[_: P]: P[ResultRef] = {
    P(location ~ Keywords.result ~/ pathIdentifier).map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def messageRef[_: P]: P[MessageReference] = { P(commandRef | eventRef | queryRef | resultRef) }

  def entityRef[_: P]: P[EntityRef] = {
    P(location ~ Keywords.entity ~/ pathIdentifier).map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def topicRef[_: P]: P[TopicRef] = {
    P(location ~ Keywords.topic ~/ pathIdentifier).map(tpl => (TopicRef.apply _).tupled(tpl))
  }

  def typeRef[_: P]: P[TypeRef] = {
    P(location ~ pathIdentifier).map(tpl => (TypeRef.apply _).tupled(tpl))
  }

  def actionRef[_: P]: P[FunctionRef] = {
    P(location ~ Keywords.action ~/ pathIdentifier).map(tpl => (FunctionRef.apply _).tupled(tpl))
  }

  def contextRef[_: P]: P[ContextRef] = {
    P(location ~ Keywords.context ~/ pathIdentifier).map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[_: P]: P[DomainRef] = {
    P(location ~ Keywords.domain ~/ pathIdentifier).map(tpl => (DomainRef.apply _).tupled(tpl))
  }
}
