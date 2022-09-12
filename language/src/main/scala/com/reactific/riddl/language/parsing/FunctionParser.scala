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
import com.reactific.riddl.language.Terminals.{Keywords, Options, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait FunctionParser extends CommonParser with TypeParser with GherkinParser {

  def functionOptions[X: P]: P[Seq[FunctionOption]] = {
    options[X, FunctionOption](
      StringIn(Options.tail_recursive).!
    ) {
      case (loc, Options.tail_recursive, _) => TailRecursive(loc)
      case (_, _, _) =>
        throw new RuntimeException("Impossible case")
    }
  }

  def functionInclude[x: P] : P[Include] = {
    include[FunctionDefinition, x](functionDefinitions(_))
  }

  def input[u: P]: P[Aggregation] = { P(Keywords.requires ~ Punctuation.colon.? ~ aggregation) }

  def output[u: P]: P[Aggregation] = { P(Keywords.returns ~ Punctuation.colon.? ~ aggregation) }

  def optionalInputOrOutput[u: P]: P[(Option[Aggregation], Option[Aggregation])] = {
    P(input.? ~ output.?)
  }

  def functionDefinitions[u:P]: P[Seq[FunctionDefinition]] = {
    P( typeDef | example | function | term |  author | functionInclude)
      .rep(0)
  }

  def functionBody[u:P]: P[(Seq[FunctionOption], Option[Aggregation],Option[Aggregation],Seq[FunctionDefinition])] = {
    P(undefined(None).map { _ =>
      (Seq.empty[FunctionOption], None, None, Seq.empty[FunctionDefinition])}
      | (functionOptions ~ input.? ~ output.? ~ functionDefinitions))
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
    P(location ~ Keywords.function ~/ identifier ~ is ~ open ~
      functionBody ~ close ~ briefly ~ description
    ).map { case (loc, id, (options, input, output, definitions), briefly, description) =>
      val groups = definitions.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val examples = mapTo[Example](groups.get(classOf[Example]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      Function(loc, id, input, output, types, functions, examples,
        authors, includes, options, terms, briefly, description)
    }
  }
}
