/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser with GherkinParser {

  def input[u: P]: P[Aggregation] = { P(Keywords.requires ~ Punctuation.colon.? ~ aggregation) }

  def output[u: P]: P[Aggregation] = { P(Keywords.yields ~ Punctuation.colon.? ~ aggregation) }

  def optionalInputOrOutput[u: P]: P[(Option[Aggregation], Option[Aggregation])] = {
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
        briefly ~ description
    ).map { case (loc, id, (inp, outp), examples, briefly, description) =>
      Function(loc, id, inp, outp, examples, briefly, description)
    }
  }
}
