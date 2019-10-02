package com.yoppworks.ossum.riddl.parser

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.parser.AST._

/** Common Parsing Rules */
object CommonParser {

  def link[_: P]: P[Link] = {
    P("link" ~ "(" ~/ literalString ~ ")"./).map(Link)
  }

  def explanation[_: P]: P[Option[Explanation]] = {
    P(
      "explained" ~ "as" ~ "{" ~ "purpose" ~ ":" ~ literalString ~
        ("details" ~ ":" ~ literalString).? ~
        link.rep(0) ~
        "}"./
    ).?.map { opt ⇒
      opt.map(tpl ⇒ { Explanation.apply _ }.tupled(tpl))
    }
  }

  def literalString[_: P]: P[LiteralString] = {
    P("\"" ~~/ CharsWhile(_ != '"', 0).! ~~ "\"").map(LiteralString)
  }

  def literalInteger[_: P]: P[LiteralInteger] = {
    P(CharIn("0-9").rep(1).!.map(_.toInt)).map(s ⇒ LiteralInteger(BigInt(s)))
  }

  def literalDecimal[_: P]: P[LiteralDecimal] = {
    P(
      CharIn("+\\-").?.! ~ CharIn("0-9").rep(1).! ~
        ("." ~ CharIn("0-9").rep(0)).?.! ~
        ("E" ~ CharIn("+\\-") ~ CharIn("0-9").rep(min = 1, max = 3)).?.!
    ).map {
      case (a, b, c, d) ⇒ LiteralDecimal(BigDecimal(a + b + c + d))
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
    P(anyIdentifier).map(Identifier)
  }

  def pathIdentifier[_: P]: P[PathIdentifier] = {
    P(anyIdentifier.repX(1, P("."))).map(PathIdentifier)
  }

  def typeRef[_: P]: P[TypeRef] = {
    P("type" ~/ identifier).map { id ⇒
      TypeRef(id)
    }
  }

  def messageRef[_: P]: P[MessageRef] = {
    P(
      P("command" ~ identifier).map(CommandRef) |
        P("event" ~ identifier).map(EventRef) |
        P("query" ~ identifier).map(QueryRef) |
        P("result" ~ identifier).map(ResultRef)
    )
  }

  def entityRef[_: P]: P[EntityRef] = {
    P("entity" ~/ identifier).map(EntityRef)
  }

  def channelRef[_: P]: P[ChannelRef] = {
    P("channel" ~/ identifier).map(ChannelRef)
  }

  def resultRef[_: P]: P[ResultRef] = {
    P("result" ~~ "s".? ~/ identifier).map(ResultRef)
  }

  def domainRef[_: P]: P[DomainRef] = {
    P(
      "domain" ~/ identifier
    ).map(DomainRef)
  }

  def contextRef[_: P]: P[ContextRef] = {
    P("context" ~/ identifier).map(ContextRef)
  }

}
