/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** SagaParser Implements the parsing of saga definitions in context definitions.
  */
private[parsing] trait SagaParser {

  this: ReferenceParser with FunctionParser with StreamingParser with StatementParser with CommonParser =>

  private def sagaStep[u: P]: P[SagaStep] = {
    P(
      location ~ Keywords.step ~/ identifier ~ is ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        Keywords.reverted ~ Readability.by.? ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        briefly ~ description ~ comments
    ).map(x => (SagaStep.apply _).tupled(x))
  }

  private def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](StringIn(RiddlOption.parallel, RiddlOption.sequential).!) {
      case (loc, option, _) if option == RiddlOption.parallel   => ParallelOption(loc)
      case (loc, option, _) if option == RiddlOption.sequential => SequentialOption(loc)
      case (loc, RiddlOption.technology, args)                  => SagaTechnologyOption(loc, args)
    }
  }

  private def sagaInclude[u: P]: P[Include[SagaDefinition]] = {
    include[SagaDefinition, u](sagaDefinitions(_))
  }

  private def sagaDefinitions[u: P]: P[Seq[SagaDefinition]] = {
    P(sagaStep | inlet | outlet | function | term | sagaInclude)./.rep(2)
  }

  private type SagaBodyType = (
    Option[Aggregation],
    Option[Aggregation],
    Seq[SagaDefinition]
  )

  private def sagaBody[u: P]: P[SagaBodyType] = {
    P(
      undefined((None, None, Seq.empty[SagaDefinition])) |
        (input.? ~ output.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ authorRefs ~ is ~ open ~
        sagaOptions ~ sagaBody ~ close ~ briefly ~ description ~ comments
    ).map {
      case (
            location,
            identifier,
            authors,
            options,
            (input, output, definitions),
            briefly,
            description,
            comments
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val steps = mapTo[SagaStep](groups.get(classOf[SagaStep]))
        val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
        val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
        val includes = mapTo[Include[SagaDefinition]](
          groups.get(
            classOf[Include[SagaDefinition]]
          )
        )
        val terms = mapTo[Term](groups.get(classOf[Term]))
        Saga(
          location,
          identifier,
          options,
          input,
          output,
          steps,
          functions,
          inlets,
          outlets,
          authors,
          includes,
          terms,
          briefly,
          description,
          comments
        )
    }
  }
}
