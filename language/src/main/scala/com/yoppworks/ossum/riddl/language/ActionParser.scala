package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse.IgnoreCase
import fastparse._
import ScalaWhitespace._
import Terminals.Punctuation
import Terminals.Keywords

/** Unit Tests For FunctionParser */
trait ActionParser extends CommonParser with TypeParser {

  def input[_: P]: P[Aggregation] = {
    P(
      Keywords.output ~ is ~ aggregationType
    )
  }

  def output[_: P]: P[Aggregation] = {
    P(
      Keywords.output ~ is ~ aggregationType
    )
  }

  def action[_: P]: P[Action] = {
    P(
      location ~ IgnoreCase(Keywords.action) ~/ identifier ~ is ~ open ~
        description ~ input ~ output ~ close ~ addendum
    ).map { tpl =>
      (Action.apply _).tupled(tpl)
    }
  }
}
