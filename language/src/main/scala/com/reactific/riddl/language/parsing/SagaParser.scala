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
import fastparse.*
import fastparse.ScalaWhitespace.*

/** SagaParser Implements the parsing of saga definitions in context
  * definitions.
  */
trait SagaParser
    extends ReferenceParser
    with ActionParser
    with GherkinParser
    with FunctionParser {

  def sagaStep[u: P]: P[SagaStep] = {
    P(
      location ~ Keywords.step ~/ identifier ~ is ~ open ~ sagaStepAction ~
        Keywords.reverted ~ Readability.by.? ~ sagaStepAction ~ close ~ as ~
        open ~ examples ~ close ~ briefly ~ description
    ).map(x => (SagaStep.apply _).tupled(x))
  }

  def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](StringIn(Options.parallel, Options.sequential).!) {
      case (loc, option, _) if option == Options.parallel => ParallelOption(loc)
      case (loc, option, _) if option == Options.sequential =>
        SequentialOption(loc)
      case (loc, Options.technology, args) => SagaTechnologyOption(loc, args)
      case (loc, option, _) =>
        throw new IllegalStateException(s"Unknown saga option $option at $loc")
    }
  }

  def sagaInclude[u: P]: P[Include[SagaDefinition]] = {
    include[SagaDefinition, u](sagaDefinitions(_))
  }

  def sagaDefinitions[u: P]: P[Seq[SagaDefinition]] = {
    P(sagaStep | author | term | sagaInclude).rep(2)
  }

  type SagaBodyType = (
    Seq[SagaOption],
    Option[Aggregation],
    Option[Aggregation],
    Seq[SagaDefinition]
  )
  def sagaBody[u: P]: P[SagaBodyType] = {
    P(
      undefined(
        (Seq.empty[SagaOption], None, None, Seq.empty[SagaDefinition])
      ) | (sagaOptions ~ input.? ~ output.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaBody ~ close ~
        briefly ~ description
    ).map {
      case (
            location,
            identifier,
            (options, input, output, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val steps = mapTo[SagaStep](groups.get(classOf[SagaStep]))
        val authors = mapTo[Author](groups.get(classOf[Author]))
        val includes =
          mapTo[Include[SagaDefinition]](groups.get(classOf[Include[SagaDefinition]]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        Saga(
          location,
          identifier,
          options,
          input,
          output,
          steps,
          authors,
          includes,
          terms,
          briefly,
          description
        )
    }
  }
}
