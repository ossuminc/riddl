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
import com.reactific.riddl.language.Terminals.{Keywords, Options, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** SagaParser Implements the parsing of saga definitions in context definitions.
  */
trait SagaParser extends ReferenceParser with ActionParser with GherkinParser with FunctionParser {

  def sagaStep[u: P]: P[SagaStep] = {
    P(
      location ~ Keywords.step ~/ identifier ~ is ~ open ~
        sagaStepAction ~ Keywords.reverted ~ Readability.by.? ~
        sagaStepAction ~ close ~ as ~ open ~ examples ~
        close ~ briefly ~ description
    ).map(x => (SagaStep.apply _).tupled(x))
  }

  def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](StringIn(Options.parallel, Options.sequential).!) {
      case (loc, option, _) if option == Options.parallel   => ParallelOption(loc)
      case (loc, option, _) if option == Options.sequential => SequentialOption(loc)
      case (loc, option, _) =>
        throw new IllegalStateException(s"Unknown saga option $option at $loc")
    }
  }

  def sagaInput[u: P]: P[Aggregation] = { P(Keywords.input ~ aggregation) }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaOptions ~ optionalInputOrOutput ~
        sagaStep.rep(2) ~ close ~ briefly ~ description
    ).map { case (location, identifier, options, (input, output), actions, briefly, description) =>
      Saga(location, identifier, options, input, output, actions, briefly, description)
    }
  }
}
