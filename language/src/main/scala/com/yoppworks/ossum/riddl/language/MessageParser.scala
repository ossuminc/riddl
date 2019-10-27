package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import com.yoppworks.ossum.riddl.language.Terminals.Readability

/** Parsing of Message definitions */
trait MessageParser extends CommonParser with TypeParser {

  def commandDef[_: P]: P[CommandDef] = {
    P(
      location ~ Keywords.command ~/ identifier ~ is ~ typeExpression ~
        Readability.yields ~
        eventRefsForCommandDefs ~ addendum
    ).map { tpl =>
      (CommandDef.apply _).tupled(tpl)
    }
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      location ~ Keywords.event ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (EventDef.apply _).tupled(tpl)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      location ~ Keywords.query ~/ identifier ~ is ~ typeExpression ~
        Readability.yields ~
        resultRef ~ addendum
    ).map { tpl =>
      (QueryDef.apply _).tupled(tpl)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      location ~ Keywords.result ~/ identifier ~ is ~ typeExpression ~ addendum
    ).map { tpl =>
      (ResultDef.apply _).tupled(tpl)
    }
  }

  def anyMessageDef[_: P]: P[MessageDefinition] = {
    P(commandDef | eventDef | queryDef | resultDef)
  }

  def eventRefsForCommandDefs[_: P]: P[EventRefs] = {
    P(
      eventRef.map(Seq(_)) |
        Keywords.events ~/ Punctuation.curlyOpen ~
          (location ~ identifier)
            .map { tpl =>
              (EventRef.apply _).tupled(tpl)
            }
            .rep(2) ~
          Punctuation.curlyClose
    )
  }
}
