package com.yoppworks.ossum.riddl.parser

import AST._
import fastparse._
import ScalaWhitespace._

/** Parsing of Message definitions */
trait MessageParser extends CommonParser with TypesParser {

  def commandDef[_: P]: P[CommandDef] = {
    P(
      location ~ "command" ~/ identifier ~ is ~ typeExpression ~ "yields" ~
        eventRefsForCommandDefs ~ addendum
    ).map { tpl =>
      (CommandDef.apply _).tupled(tpl)
    }
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      location ~ "event" ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (EventDef.apply _).tupled(tpl)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      location ~ "query" ~/ identifier ~ is ~ typeExpression ~ "yields" ~
        resultRef ~ addendum
    ).map { tpl =>
      (QueryDef.apply _).tupled(tpl)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      location ~ "result" ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (ResultDef.apply _).tupled(tpl)
    }
  }

  def anyMessageDef[_: P]: P[MessageDefinition] = {
    P(commandDef | eventDef | queryDef | resultDef)
  }

  def eventRefsForCommandDefs[_: P]: P[EventRefs] = {
    P(
      ("event" ~/ (location ~ identifier)
        .map(tpl => Seq((EventRef.apply _).tupled(tpl)))) |
        "events" ~ "{" ~/ (location ~/ identifier)
          .map { tpl =>
            (EventRef.apply _).tupled(tpl)
          }
          .rep(0) ~
          "}"
    )
  }
}
