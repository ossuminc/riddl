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
  this: ReferenceParser & FunctionParser & StreamingParser & StatementParser & CommonParser =>

  private def sagaStep[u: P]: P[SagaStep] = {
    P(
      location ~ Keywords.step ~/ identifier ~ is ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        Keywords.reverted ~ Readability.by.? ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        briefly ~ description
    ).map(x => SagaStep.apply.tupled(x))
  }

  private def sagaInclude[u: P]: P[IncludeHolder[OccursInSaga]] = {
    include[u, OccursInSaga](sagaDefinitions(_))
  }

  private def sagaDefinitions[u: P]: P[Seq[OccursInSaga]] = {
    P(sagaStep | inlet | outlet | function | term | sagaInclude | option)./.rep(2)
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
      location ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaBody ~ close ~ briefly ~ description
    ).map { case (location, identifier, (input, output, contents), briefly, description) =>
      val mergedContent = mergeAsynchContent[OccursInSaga](contents)
      Saga(location, identifier, input, output, mergedContent, briefly, description)
    }
  }
}
