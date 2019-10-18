package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ChannelParser */
trait ChannelParser extends CommonParser {

  def commandRefs[_: P]: P[Seq[CommandRef]] = {
    P(
      "command" ~~ "s".? ~ open ~/
        (location ~ identifier)
          .map(
            tpl => (CommandRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ close
    )
  }

  def eventRefs[_: P]: P[Seq[EventRef]] = {
    P(
      "event" ~~ "s".? ~ open ~/
        (location ~ identifier)
          .map(
            tpl => (EventRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ close
    )
  }

  def queryRefs[_: P]: P[Seq[QueryRef]] = {
    P(
      ("query" | "queries") ~ open ~
        (location ~ identifier)
          .map(
            tpl => (QueryRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ close
    )
  }

  def resultRefs[_: P]: P[Seq[ResultRef]] = {
    P(
      "result" ~~ "s".? ~ open ~
        (location ~ identifier)
          .map(
            tpl => (ResultRef.apply _).tupled(tpl)
          )
          .rep(0, ",") ~ close
    )
  }

  def channelDef[_: P]: P[ChannelDef] = {
    P(
      location ~ "channel" ~/ identifier ~ open ~
        commandRefs ~ eventRefs ~ queryRefs ~ resultRefs ~
        close ~/ addendum
    ).map { tpl =>
      (ChannelDef.apply _).tupled(tpl)
    }
  }

}
