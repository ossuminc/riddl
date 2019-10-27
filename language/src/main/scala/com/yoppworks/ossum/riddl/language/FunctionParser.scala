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
      Punctuation.curlyOpen ~ typeExpression.rep(min = 0, Punctuation.comma) ~
        Punctuation.curlyClose
    )
  }

  def outputs[_: P]: P[Seq[TypeExpression]] = {
    P(
      Punctuation.colon ~ Punctuation.curlyOpen ~ typeExpression
        .rep(min = 0, Punctuation.comma) ~
        Punctuation.curlyClose
    )
  }

  def functionDef[_: P]: P[FunctionDef] = {
    P(
      location ~ IgnoreCase(Keywords.function) ~/ identifier ~
        inputs ~ Punctuation.colon ~ outputs ~ lines ~/ addendum
    ).map { tpl =>
      (FunctionDef.apply _).tupled(tpl)
    }
  }
}
