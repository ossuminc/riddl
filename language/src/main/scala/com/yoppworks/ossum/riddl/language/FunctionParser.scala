package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse.IgnoreCase
import fastparse._
import ScalaWhitespace._

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser {

  def inputs[_: P]: P[Seq[TypeExpression]] = {
    P(open ~ typeExpression.rep(min = 0, ",") ~ close)
  }

  def outputs[_: P]: P[Seq[TypeExpression]] = {
    P(":" ~ open ~ typeExpression.rep(min = 0, ",") ~ close)
  }

  def functionDef[_: P]: P[FunctionDef] = {
    P(
      location ~ IgnoreCase("function") ~/ identifier ~
        inputs ~ ":" ~ outputs ~ lines ~/ addendum
    ).map { tpl =>
      (FunctionDef.apply _).tupled(tpl)
    }
  }
}
