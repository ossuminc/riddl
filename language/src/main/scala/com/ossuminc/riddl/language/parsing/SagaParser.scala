/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** SagaParser Implements the parsing of saga definitions in referent definitions.
  */
private[parsing] trait SagaParser {
  this: ProcessorParser & FunctionParser & StreamingParser & StatementParser =>

  def sagaStep[u: P]: P[SagaStep] = {
    P(
      Index ~ Keywords.step ~/ identifier ~ is ~ pseudoCodeBlock(StatementsSet.SagaStatements) ~
        Keywords.reverted ~ by.? ~ pseudoCodeBlock(
          StatementsSet.SagaStatements
        ) ~ withMetaData ~ Index
    )./.map { case (start, id, doStatements, undoStatements, descriptives, end) =>
      SagaStep(
        at(start, end),
        id,
        doStatements.toContents,
        undoStatements.toContents,
        descriptives.toContents
      )
    }
  }

  private def sagaInclude[u: P]: P[Include[SagaContents]] = {
    include[u, SagaContents]((p: P[?]) => sagaDefinitions(using p.asInstanceOf[P[u]]))
  }

  /** A55/saga: `vitalDefinitionContents` (`typeDef | comment`) leads, as it does in every other
    * container of this family — DomainParser:38, FunctionParser:35, EpicParser:170,
    * ProcessorParser:69. Saga was the only one omitting it, which is why a `//` comment between
    * two steps was a PARSE ERROR whose message never mentioned comments.
    *
    * This is not a widening of what a Saga may hold: `OccursInSaga` is
    * `OccursInVitalDefinition | SagaStep` (AST.scala:931), so `Type` and `Comment` were always
    * legal saga contents. Only this rule disagreed.
    *
    * The `rep(2)` is retained but is NOT the guard it looks like: the real rule — a saga needs at
    * least two STEPS — lives in ValidationPass:2585, which counts `sagaSteps` specifically and
    * reports a proper Error with a suggestion. So a body whose two contents are comments now
    * parses and then earns that message, which is strictly better than a parse failure pointing at
    * the wrong token.
    */
  private[parsing] def sagaDefinitions[u: P]: P[Seq[SagaContents]] = {
    P(
      vitalDefinitionContents | sagaStep | inlet | outlet | function | sagaInclude
    ).asInstanceOf[P[SagaContents]]./.rep(2)
  }

  private type SagaBodyType = (
    Option[TypeRef | Aggregation],
    Option[TypeRef | Aggregation],
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
      Saga(at(start, end), identifier, input, output, contents.toContents, descriptives.toContents)
    }
  }
}
