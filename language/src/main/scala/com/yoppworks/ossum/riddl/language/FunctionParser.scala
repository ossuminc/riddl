package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import fastparse.IgnoreCase
import fastparse.*
import ScalaWhitespace.*
import Terminals.Keywords

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser {

  def input[u: P]: P[TypeExpression] = { P(Keywords.requires ~ typeExpression) }

  def output[u: P]: P[TypeExpression] = { P(Keywords.yields ~ typeExpression) }

  /** Parses function literals, i.e.
    *
    * {{{
    *   function myFunction is {
    *     requires is Boolean
    *     yields is Integer
    *   }
    * }}}
    */
  def function[u: P]: P[Function] = {
    P(
      location ~ IgnoreCase(Keywords.function) ~/ identifier ~ is ~ open ~
        ((location ~ undefined(None)).map { case (l, n) => (n, Nothing(l)) } | (input.? ~ output)) ~
        close ~ description
    ).map { case (loc, id, (inp, outp), descr) => Function(loc, id, inp, outp, descr) }
  }
}
