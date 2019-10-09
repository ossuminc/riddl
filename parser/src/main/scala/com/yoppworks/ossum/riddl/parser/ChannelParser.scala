package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ChannelParser */
trait ChannelParser extends CommonParser {

  def commandRefs[_: P]: P[Seq[CommandRef]] = {
    P(
      "command" ~~ "s".? ~ "{" ~/
        (location ~ identifier)
          .map(
            tpl => (CommandRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ "}"./
    )
  }

  def eventRefs[_: P]: P[Seq[EventRef]] = {
    P(
      "event" ~~ "s".? ~ "{" ~/
        (location ~ identifier)
          .map(
            tpl => (EventRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ "}"./
    )
  }

  def queryRefs[_: P]: P[Seq[QueryRef]] = {
    P(
      ("query" | "queries") ~ "{" ~/
        (location ~ identifier)
          .map(
            tpl => (QueryRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ "}"./
    )
  }

  def resultRefs[_: P]: P[Seq[ResultRef]] = {
    P(
      "result" ~~ "s".? ~ "{" ~/
        (location ~ identifier)
          .map(
            tpl => (ResultRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ "}"./
    )
  }

  def channelDef[_: P]: P[ChannelDef] = {
    P(
      location ~ "channel" ~/ identifier ~ "{" ~
        commandRefs ~ eventRefs ~ queryRefs ~ resultRefs ~
        "}" ~/ addendum
    ).map { tpl =>
      (ChannelDef.apply _).tupled(tpl)
    }
  }

}
