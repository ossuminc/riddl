/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** SagaParser Implements the parsing of saga definitions in referent definitions.
  */
private[parsing] trait SagaParser {
  this: ProcessorParser & FunctionParser & StreamingParser & StatementParser =>

  def sagaStep[u: P]: P[SagaStep] = {
    P(
      Index ~ Keywords.step ~/ identifier ~ is ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        Keywords.reverted ~ by.? ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~ withMetaData ~ Index
    )./.map { case (start, id, doStatements, undoStatements, descriptives, end) =>
      SagaStep(at(start, end), id, doStatements.toContents, undoStatements.toContents, descriptives.toContents)
    }
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
        (funcInput.? ~ funcOutput.? ~ sagaDefinitions)
    )
  }

  def saga[u: P]: P[Saga] = {
    P(
      Index ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaBody ~ close ~ withMetaData ~ Index
    ).map { case (start, identifier, (input, output, contents), descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Saga(at(start,end), identifier, input, output, contents.toContents, descriptives.toContents)
    }
  }
}
