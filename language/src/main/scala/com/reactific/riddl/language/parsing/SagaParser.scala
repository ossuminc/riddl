/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** SagaParser Implements the parsing of saga definitions in context
  * definitions.
  */
private[parsing] trait SagaParser
    extends ReferenceParser
    with ActionParser
    with GherkinParser
    with FunctionParser {

  private def sagaStep[u: P]: P[SagaStep] = {
    P(
      location ~ Keywords.step ~/ identifier ~ is ~ open ~ examples ~
        Keywords.reverted ~ Readability.by.? ~ examples ~ close ~ briefly ~
        description
    ).map(x => (SagaStep.apply _).tupled(x))
  }

  private def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](StringIn(Options.parallel, Options.sequential).!) {
      case (loc, option, _) if option == Options.parallel => ParallelOption(loc)
      case (loc, option, _) if option == Options.sequential =>
        SequentialOption(loc)
      case (loc, Options.technology, args) => SagaTechnologyOption(loc, args)
      case (loc, option, _) =>
        throw new IllegalStateException(s"Unknown saga option $option at $loc")
    }
  }

  private def sagaInclude[u: P]: P[Include[SagaDefinition]] = {
    include[SagaDefinition, u](sagaDefinitions(_))
  }

  private def sagaDefinitions[u: P]: P[Seq[SagaDefinition]] = {
    P(sagaStep | function | term | sagaInclude).rep(2)
  }

  private type SagaBodyType = (
    Seq[SagaOption],
    Option[Aggregation],
    Option[Aggregation],
    Seq[SagaDefinition]
  )

  private def sagaBody[u: P]: P[SagaBodyType] = {
    P(
      undefined(
        (Seq.empty[SagaOption], None, None, Seq.empty[SagaDefinition])
      ) | (sagaOptions ~ input.? ~ output.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ authorRefs ~ is ~ open ~
        sagaBody ~ close ~ briefly ~ description
    ).map {
      case (
            location,
            identifier,
            authors,
            (options, input, output, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val steps = mapTo[SagaStep](groups.get(classOf[SagaStep]))
        val includes = mapTo[Include[SagaDefinition]](groups.get(
          classOf[Include[SagaDefinition]]
        ))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        Saga(
          location,
          identifier,
          options,
          input,
          output,
          steps,
          functions,
          authors,
          includes,
          terms,
          briefly,
          description
        )
    }
  }
}
