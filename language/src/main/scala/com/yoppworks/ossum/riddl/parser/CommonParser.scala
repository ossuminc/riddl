package com.yoppworks.ossum.riddl.parser

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.parser.AST._

/** Common Parsing Rules */
trait CommonParser extends ParsingContext {

  def location[_: P]: P[Location] = {
    P(Index).map(input.location)
  }

  def literalString[_: P]: P[LiteralString] = {
    P(
      location ~ "\"" ~~/ CharsWhile(_ != '"', 0).! ~~ "\""
    ).map { tpl =>
      (LiteralString.apply _).tupled(tpl)
    }
  }

  def lines[_: P]: P[Seq[String]] = {
    P("{" ~ literalString.rep(0) ~ "}").map { lit =>
      lit.map(_.s)
    }
  }

  def seeAlso[_: P]: P[SeeAlso] = {
    P(
      location ~ "see" ~ "also" ~ lines./
    ).map(tpl => (SeeAlso.apply _).tupled(tpl))
  }

  def explanation[_: P]: P[Explanation] = {
    P(
      location ~
        "explained" ~ "as" ~/ lines
    ).map(tpl => { Explanation.apply _ }.tupled(tpl))
  }

  def addendum[_: P]: P[Option[Addendum]] = {
    P(location ~ explanation.? ~ seeAlso.?).map[Option[Addendum]] {
      case (loc, None, None)                  => None
      case (loc, exp @ Some(_), None)         => Some(Addendum(loc, exp, None))
      case (loc, exp @ Some(_), sa @ Some(_)) => Some(Addendum(loc, exp, sa))
      case (loc, None, sa @ Some(_))          => Some(Addendum(loc, None, sa))
    }
  }

  def literalInteger[_: P]: P[LiteralInteger] = {
    P(location ~ CharIn("0-9").rep(1).!.map(_.toInt))
      .map(s => LiteralInteger(s._1, BigInt(s._2)))
  }

  def literalDecimal[_: P]: P[LiteralDecimal] = {
    P(
      location ~
        CharIn("+\\-").?.! ~ CharIn("0-9").rep(1).! ~
        ("." ~ CharIn("0-9").rep(0)).?.! ~
        ("E" ~ CharIn("+\\-") ~ CharIn("0-9").rep(min = 1, max = 3)).?.!
    ).map {
      case (loc, a, b, c, d) => LiteralDecimal(loc, BigDecimal(a + b + c + d))
    }
  }

  def simpleIdentifier[_: P]: P[String] = {
    P((CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_").?).!)
  }

  def quotedIdentifier[_: P]: P[String] = {
    P("'" ~/ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~ "'")
  }

  def anyIdentifier[_: P]: P[String] = {
    P(simpleIdentifier | quotedIdentifier)
  }

  def identifier[_: P]: P[Identifier] = {
    P(location ~ anyIdentifier).map(tpl => (Identifier.apply _).tupled(tpl))
  }

  def pathIdentifier[_: P]: P[PathIdentifier] = {
    P(location ~ anyIdentifier.repX(1, P(".")))
      .map(tpl => (PathIdentifier.apply _).tupled(tpl))
  }

  def is[_: P]: P[Unit] = {
    P("is" | "are" | ":" | "=")./
  }

  def options[_: P, TY <: RiddlValue](
    validOptions: => P[String]
  )(mapper: => (Location, String) => TY): P[Seq[TY]] = {
    P(
      ("options" ~/ "{" ~ (location ~ validOptions)
        .rep(2)
        .map(_.map(mapper.tupled(_))) ~ "}") | ("option" ~ is ~/ (location ~ validOptions)
        .map(
          tpl => Seq(mapper.tupled(tpl))
        )) | P(
        ""
      ).map { _ =>
        Seq.empty[TY]
      }
    )
  }

  def commandRef[_: P]: P[CommandRef] = {
    P(location ~ "command" ~/ identifier)
      .map(tpl => (CommandRef.apply _).tupled(tpl))
  }

  def eventRef[_: P]: P[EventRef] = {
    P(location ~ "event" ~/ identifier)
      .map(tpl => (EventRef.apply _).tupled(tpl))
  }

  def queryRef[_: P]: P[QueryRef] = {
    P(location ~ "query" ~/ identifier)
      .map(tpl => (QueryRef.apply _).tupled(tpl))
  }

  def resultRef[_: P]: P[ResultRef] = {
    P(location ~ "result" ~/ identifier)
      .map(tpl => (ResultRef.apply _).tupled(tpl))
  }

  def messageRef[_: P]: P[MessageReference] = {
    P(commandRef | eventRef | queryRef | resultRef)
  }

  def entityRef[_: P]: P[EntityRef] = {
    P(location ~ "entity" ~/ identifier)
      .map(tpl => (EntityRef.apply _).tupled(tpl))
  }

  def channelRef[_: P]: P[ChannelRef] = {
    P(location ~ "channel" ~/ identifier)
      .map(tpl => (ChannelRef.apply _).tupled(tpl))
  }

  def contextRef[_: P]: P[ContextRef] = {
    P(location ~ "context" ~/ identifier)
      .map(tpl => (ContextRef.apply _).tupled(tpl))
  }

  def domainRef[_: P]: P[DomainRef] = {
    P(location ~ "domain" ~/ identifier)
      .map(tpl => (DomainRef.apply _).tupled(tpl))
  }

}
