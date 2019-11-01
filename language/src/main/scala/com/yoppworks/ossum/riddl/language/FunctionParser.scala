package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse.IgnoreCase
import fastparse._
import ScalaWhitespace._
import Terminals.Punctuation
import Terminals.Keywords

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser {

  def inputs[_: P]: P[Seq[TypeExpression]] = {
    P(
      open ~ typeExpression.rep(min = 0, Punctuation.comma) ~
        close
    )
  }

  def outputs[_: P]: P[Seq[TypeExpression]] = {
    P(
      open ~ typeExpression.rep(min = 0, Punctuation.comma) ~
        close
    )
  }

  def functionDef[_: P]: P[Function] = {
    P(
      location ~ IgnoreCase(Keywords.function) ~/ identifier ~ is ~
        inputs ~ Punctuation.colon ~ outputs ~ docBlock ~/ addendum
    ).map { tpl =>
      (Function.apply _).tupled(tpl)
    }
  }
}
