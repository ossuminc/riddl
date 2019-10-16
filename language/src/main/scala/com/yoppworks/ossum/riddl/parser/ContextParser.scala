package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for Context definitions */
trait ContextParser
    extends AdaptorParser
    with EntityParser
    with InteractionParser
    with MessageParser
    with TypeParser {

  def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](StringIn("wrapper", "function", "gateway").!) {
      case (loc, "wrapper")  => WrapperOption(loc)
      case (loc, "function") => FunctionOption(loc)
      case (loc, "gateway")  => GatewayOption(loc)
      case (loc, _)          => throw new RuntimeException("Impossible case")
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      location ~ "context" ~/ identifier ~ "{" ~
        contextOptions ~
        typeDef.rep(0) ~
        commandDef.rep(0) ~
        eventDef.rep(0) ~
        queryDef.rep(0) ~
        resultDef.rep(0) ~
        entityDef.rep(0) ~
        adaptorDef.rep(0) ~
        interactionDef.rep(0) ~
        "}" ~ addendum
    ).map { args =>
      (ContextDef.apply _).tupled(args)
    }
  }
}
