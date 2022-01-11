package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser with GherkinParser {

  def input[u: P]: P[TypeExpression] = {
    P(Keywords.requires ~ Punctuation.colon.? ~ typeExpression)
  }

  def output[u: P]: P[TypeExpression] = {
    P(Keywords.yields ~ Punctuation.colon.? ~ typeExpression)
  }

  def optionalInputOrOutput[u: P]: P[(Option[TypeExpression], Option[TypeExpression])] = {
    P(input.? ~ output.?)
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
        (undefined(None).map { n => (n, None) } | optionalInputOrOutput) ~ examples ~ close ~
        description
    ).map { case (loc, id, (inp, outp), examples, descr) =>
      Function(loc, id, inp, outp, examples, descr)
    }
  }
}
