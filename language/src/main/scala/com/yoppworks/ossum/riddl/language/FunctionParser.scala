package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse.IgnoreCase
import fastparse._
import ScalaWhitespace._
import Terminals.Punctuation
import Terminals.Keywords

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser {

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

  /** Parses function literals, i.e.
    *
    * {{{
    *   function myFunction is {
    *     requires is Boolean
    *     yields is Integer
    *   }
    * }}}
    */
  def function[_: P]: P[Function] = {
    P(
      location ~ IgnoreCase(Keywords.function) ~/ identifier ~ is ~ open ~
        ((location ~ undefined).map(loc => (None, Nothing(loc))) |
          (input.? ~ output)) ~
        close ~ description
    ).map {
      case (loc, id, (inp, outp), descr) =>
        Function(loc, id, inp, outp, descr)
    }
  }
}
