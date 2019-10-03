package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import TypesParser._
import CommonParser._
import com.yoppworks.ossum.riddl.parser.TopLevelParser.adaptorDef
import com.yoppworks.ossum.riddl.parser.EntityParser._
import com.yoppworks.ossum.riddl.parser.InteractionParser.interactionDef
import com.yoppworks.ossum.riddl.parser.TypesParser.typeDef
import fastparse._
import ScalaWhitespace._

/** Parsing rules for Context definitions */
object ContextParser {

  def contextOptions[_: P]: P[ContextOption] = {
    P(StringIn("wrapper", "function", "gateway")).!.map {
      case "wrapper" ⇒ WrapperOption
      case "function" ⇒ FunctionOption
      case "gateway" ⇒ GatewayOption
    }
  }

  def commandDef[_: P]: P[CommandDef] = {
    P(
      "command" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ "yields" ~
        eventRefs ~ explanation
    ).map { tpl ⇒
      (CommandDef.apply _).tupled(tpl)
    }
  }

  def eventRefs[_: P]: P[EventRefs] = {
    P("event" ~~ "s".? ~/ identifier.rep(1, P(","))).map(_.map(EventRef))
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      "event" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ explanation
    ).map { tpl ⇒
      (EventDef.apply _).tupled(tpl)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      "query" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ "yields" ~
        resultRef ~ explanation
    ).map { tpl ⇒
      (QueryDef.apply _).tupled(tpl)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      "result" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ explanation
    ).map { tpl ⇒
      (ResultDef.apply _).tupled(tpl)
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      contextOptions.rep(0) ~ "context" ~ Index ~/ identifier ~ "{" ~
        typeDef.rep(0) ~ commandDef.rep(0) ~ eventDef.rep(0) ~
        queryDef.rep(0) ~ resultDef.rep(0) ~
        entityDef.rep(0) ~ adaptorDef.rep(0) ~ interactionDef.rep(0) ~
        "}" ~ explanation
    ).map { args =>
      (ContextDef.apply _).tupled(args)
    }
  }

}
