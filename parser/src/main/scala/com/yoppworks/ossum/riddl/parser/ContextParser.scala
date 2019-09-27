package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import CommonParser._
import com.yoppworks.ossum.riddl.parser.TopLevelParser.adaptorDef
import com.yoppworks.ossum.riddl.parser.EntityParser._
import com.yoppworks.ossum.riddl.parser.InteractionParser.interactionDef
import com.yoppworks.ossum.riddl.parser.TypesParser.typeDef
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ContextParser */
object ContextParser {

  def contextOptions[_: P]: P[ContextOption] = {
    P(StringIn("wrapper", "function", "gateway")).!.map {
      case "wrapper" ⇒ WrapperOption
      case "function" ⇒ FunctionOption
      case "gateway" ⇒ GatewayOption
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      contextOptions.rep(0) ~ "context" ~ Index ~/ identifier ~ "{" ~
        typeDef.rep(0) ~ commandDef.rep(0) ~ eventDef.rep(0) ~
        queryDef.rep(0) ~ resultDef.rep(0) ~
        entityDef.rep(0) ~ adaptorDef.rep(0) ~ interactionDef.rep(0) ~
        "}"
    ).map { args =>
      (ContextDef.apply _).tupled(args)
    }
  }

}
