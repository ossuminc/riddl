package com.yoppworks.ossum.riddl.parser

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.parser.AST._

/** Unit Tests For CommonParser */
object CommonParser {

  def literalString[_: P]: P[String] = {
    P("\"" ~~/ CharsWhile(_ != '"', 0).! ~~ "\"")
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

  def typeRef[_: P]: P[TypeRef] = {
    P("type" ~/ identifier).map(TypeRef)
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
      "domain" ~/ pathIdentifier
    ).map(names ⇒ DomainRef(names.mkString(".")))
  }

  def contextRef[_: P]: P[ContextRef] = {
    P("context" ~/ identifier).map(ContextRef)
  }

}
