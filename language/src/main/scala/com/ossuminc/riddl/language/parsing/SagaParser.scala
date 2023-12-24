/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
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
        briefly ~ description
    ).map(x => (SagaStep.apply _).tupled(x))
  }

  private def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](RiddlOptions.sagaOptions) {
      case (loc, RiddlOption.technology, args)                  => SagaTechnologyOption(loc, args)
      case (loc, RiddlOption.color, args)                       => SagaColorOption(loc, args)
      case (loc, RiddlOption.kind, args)                        => SagaKindOption(loc, args)
      case (loc, option, _) if option == RiddlOption.parallel   => ParallelOption(loc)
      case (loc, option, _) if option == RiddlOption.sequential => SequentialOption(loc)
    }
  }

  private def sagaInclude[u: P]: P[Include[OccursInSaga]] = {
    include[OccursInSaga, u](sagaDefinitions(_))
  }

  private def sagaDefinitions[u: P]: P[Seq[OccursInSaga]] = {
    P(sagaStep | inlet | outlet | function | term | sagaInclude)./.rep(2)
  }

  private type SagaBodyType = (
    Option[Aggregation],
    Option[Aggregation],
    Seq[OccursInSaga]
  )

  private def sagaBody[u: P]: P[SagaBodyType] = {
    P(
      undefined((None, None, Seq.empty[OccursInSaga])) |
        (input.? ~ output.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ is ~ open ~
        sagaOptions ~ sagaBody ~ close ~ briefly ~ description
    ).map { case (location, identifier, options, (input, output, contents), briefly, description) =>
      Saga(location, identifier, options, input, output, contents, briefly, description)
    }
  }
}
