package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser with FeatureParser {

  def input[u: P]: P[TypeExpression] = {
    P(Keywords.requires ~ Punctuation.colon.? ~ typeExpression)
  }

  def output[u: P]: P[TypeExpression] = {
    P(Keywords.yields ~ Punctuation.colon.? ~ typeExpression)
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
  def function[u: P]: P[Function] = {
    P(
      location ~ IgnoreCase(Keywords.function) ~/ identifier ~ is ~ open ~
        (undefined(None).map { n => (n, None) } | (input.? ~ output.?)) ~ examples ~ close ~
        description
    ).map { case (loc, id, (inp, outp), examples, descr) =>
      Function(loc, id, inp, outp, examples, descr)
    }
  }
}
