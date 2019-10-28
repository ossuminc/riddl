package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Readability

/** Unit Tests For ChannelParser */
trait TopicParser extends CommonParser with TypeParser {

  def commandDef[_: P]: P[CommandDef] = {
    P(
      location ~ identifier ~ is ~ typeExpression ~
        Readability.yields ~
        eventRefsForCommandDefs ~ addendum
    ).map(
      tpl => (CommandDef.apply _).tupled(tpl)
    )

  }

  def eventRefsForCommandDefs[_: P]: P[EventRefs] = {
    P(
      eventRef.map(Seq(_)) |
        Keywords.events ~/ open ~
          (location ~ identifier)
            .map { tpl =>
              (EventRef.apply _).tupled(tpl)
            }
            .rep(2) ~
          close
    )
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      location ~ identifier ~ is ~ typeExpression ~ addendum
    ).map(
      tpl => (EventDef.apply _).tupled(tpl)
    )

  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      location ~ identifier ~ is ~ typeExpression ~
        Readability.yields ~ resultRef ~ addendum
    ).map(
      tpl => (QueryDef.apply _).tupled(tpl)
    )

  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      location ~ identifier ~ is ~ typeExpression ~ addendum
    ).map(
      tpl => (ResultDef.apply _).tupled(tpl)
    )
  }

  type TopicDefinitions =
    (Seq[CommandDef], Seq[EventDef], Seq[QueryDef], Seq[ResultDef])

  def topicDefinitions[_: P]: P[TopicDefinitions] = {
    P(
      Keywords.commands ~/ open ~ commandDef.rep ~ close |
        Keywords.events ~/ open ~ eventDef.rep ~ close |
        Keywords.queries ~/ open ~ queryDef.rep ~ close |
        Keywords.results ~/ open ~ resultDef.rep ~ close |
        Keywords.command ~/ commandDef.map(Seq(_)) |
        Keywords.event ~/ eventDef.map(Seq(_)) |
        Keywords.query ~/ queryDef.map(Seq(_)) |
        Keywords.result ~/ resultDef.map(Seq(_))
    ).rep(0).map { seq =>
      val groups = seq.flatten.groupBy(_.getClass)
      (
        mapTo[CommandDef](groups.get(classOf[CommandDef])),
        mapTo[EventDef](groups.get(classOf[EventDef])),
        mapTo[QueryDef](groups.get(classOf[QueryDef])),
        mapTo[ResultDef](groups.get(classOf[ResultDef]))
      )
    }
  }

  def topicDef[_: P]: P[TopicDef] = {
    P(
      location ~ Keywords.topic ~/ identifier ~ is ~
        open ~/
        topicDefinitions ~
        close ~/ addendum
    ).map {
      case (loc, id, (commands, events, queries, results), addendum) =>
        TopicDef(loc, id, commands, events, queries, results, addendum)
    }
  }

}
