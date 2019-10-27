package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation

/** Unit Tests For ChannelParser */
trait TopicParser extends CommonParser {

  def commandRefs[_: P]: P[Seq[CommandRef]] = {
    P(
      Keywords.command ~~ "s".? ~ Punctuation.curlyOpen ~/
        (location ~ identifier)
          .map(
            tpl => (CommandRef.apply _).tupled(tpl)
          )
          .rep(0, Punctuation.comma) ~ Punctuation.curlyClose
    )
  }

  def eventRefs[_: P]: P[Seq[EventRef]] = {
    P(
      Keywords.event ~~ "s".? ~ Punctuation.curlyOpen ~/
        (location ~ identifier)
          .map(
            tpl => (EventRef.apply _).tupled(tpl)
          )
          .rep(0, Punctuation.comma) ~ Punctuation.curlyClose
    )
  }

  def queryRefs[_: P]: P[Seq[QueryRef]] = {
    P(
      (Keywords.query | Keywords.queries) ~ Punctuation.curlyOpen ~
        (location ~ identifier)
          .map(
            tpl => (QueryRef.apply _).tupled(tpl)
          )
          .rep(0, Punctuation.comma) ~ Punctuation.curlyClose
    )
  }

  def resultRefs[_: P]: P[Seq[ResultRef]] = {
    P(
      Keywords.result ~~ "s".? ~ Punctuation.curlyOpen ~
        (location ~ identifier)
          .map(
            tpl => (ResultRef.apply _).tupled(tpl)
          )
          .rep(0, Punctuation.comma) ~ Punctuation.curlyClose
    )
  }

  def topicDef[_: P]: P[TopicDef] = {
    P(
      location ~ Keywords.topic ~/ identifier ~ Punctuation.curlyOpen ~
        commandRefs ~ eventRefs ~ queryRefs ~ resultRefs ~
        Punctuation.curlyClose ~/ addendum
    ).map { tpl =>
      (TopicDef.apply _).tupled(tpl)
    }
  }

}
