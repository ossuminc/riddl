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

  private def sagaInclude[u: P]: P[Include[SagaContents]] = {
    include[u, SagaContents](sagaDefinitions(_))
  }

  private def sagaDefinitions[u: P]: P[Seq[SagaContents]] = {
    P(
      sagaStep | inlet | outlet | function | term | sagaInclude | option
    ).asInstanceOf[P[SagaContents]]./.rep(2)
  }

  private type SagaBodyType = (
    Option[Aggregation],
    Option[Aggregation],
    Seq[SagaContents]
  )

  private def sagaBody[u: P]: P[SagaBodyType] = {
    P(
      undefined((None, None, Seq.empty[SagaContents])) |
        (input.? ~ output.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaBody ~ close ~ briefly ~ description
    ).map { case (location, identifier, (input, output, contents), briefly, description) =>
      checkForDuplicateIncludes(contents)
      Saga(location, identifier, input, output, contents, briefly, description)
    }
  }
}
