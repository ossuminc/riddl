package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse.IgnoreCase
import fastparse._
import ScalaWhitespace._
import Terminals.Punctuation
import Terminals.Keywords

/** Unit Tests For FunctionParser */
trait ActionParser extends CommonParser with TypeParser {

  def input[_: P]: P[TypeExpression] = {
    P(
      Keywords.requires ~ is ~ typeExpression
    )
  }

  def output[_: P]: P[TypeExpression] = {
    P(
      Keywords.yields ~ is ~ typeExpression
    )
  }

  def action[_: P]: P[Action] = {
    P(
      location ~ IgnoreCase(Keywords.action) ~/ identifier ~ is ~ open ~
        input.? ~ output ~ close ~ description
    ).map { tpl =>
      (Action.apply _).tupled(tpl)
    }
  }
}
