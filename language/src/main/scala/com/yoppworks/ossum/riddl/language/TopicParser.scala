package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords

/** Unit Tests For ChannelParser */
trait TopicParser extends CommonParser with TypeParser {

  def commandDefBody[_: P]: P[Command] = {
    P(
      location ~ identifier ~ is ~ typeExpression ~ Keywords.yields ~ eventRefsForCommandDefs ~
        description
    ).map(tpl => (Command.apply _).tupled(tpl))
  }

  def eventRefsForCommandDefs[_: P]: P[EventRefs] = {
    P(eventRef.map(Seq(_)) | Keywords.events ~/ open ~ (location ~ pathIdentifier).map { tpl =>
      (EventRef.apply _).tupled(tpl)
    }.rep(2) ~ close)
  }

  def commandDef[_: P]: P[Command] = { P(Keywords.command ~ commandDefBody) }

  def eventDefBody[_: P]: P[Event] = {
    P(location ~ identifier ~ is ~ typeExpression ~ description)
      .map(tpl => (Event.apply _).tupled(tpl))
  }

  def eventDef[_: P]: P[Event] = { P(Keywords.event ~ eventDefBody) }

  def queryDefBody[_: P]: P[Query] = {
    P(location ~ identifier ~ is ~ typeExpression ~ Keywords.yields ~ resultRef ~ description)
      .map(tpl => (Query.apply _).tupled(tpl))
  }

  def queryDef[_: P]: P[Query] = { P(Keywords.query ~ queryDefBody) }

  def resultDefBody[_: P]: P[Result] = {
    P(location ~ identifier ~ is ~ typeExpression ~ description)
      .map(tpl => (Result.apply _).tupled(tpl))
  }

  def resultDef[_: P]: P[Result] = { P(Keywords.result ~ resultDefBody) }

  type TopicDefinitions = (Seq[Command], Seq[Event], Seq[Query], Seq[Result])

  def topicDefinitions[_: P]: P[TopicDefinitions] = {
    P(
      Keywords.commands ~/ open ~ commandDefBody.rep ~ close |
        Keywords.events ~/ open ~ eventDefBody.rep ~ close |
        Keywords.queries ~/ open ~ queryDefBody.rep ~ close |
        Keywords.results ~/ open ~ resultDefBody.rep ~ close | commandDef.map(Seq(_))./ |
        eventDef.map(Seq(_))./ | queryDef.map(Seq(_))./ | resultDef.map(Seq(_))
    ).rep(0).map { seq =>
      val groups = seq.flatten.groupBy(_.getClass)
      (
        mapTo[Command](groups.get(classOf[Command])),
        mapTo[Event](groups.get(classOf[Event])),
        mapTo[Query](groups.get(classOf[Query])),
        mapTo[Result](groups.get(classOf[Result]))
      )
    }
  }

  def topic[_: P]: P[Topic] = {
    P(
      location ~ Keywords.topic ~/ identifier ~ is ~ open ~/
        (undefined
          .map(_ => (Seq.empty[Command], Seq.empty[Event], Seq.empty[Query], Seq.empty[Result])) |
          topicDefinitions) ~ close ~/ description
    ).map { case (loc, id, (commands, events, queries, results), addendum) =>
      Topic(loc, id, commands, events, queries, results, addendum)
    }
  }

}
