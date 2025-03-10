/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Unit Tests For FunctionParser */
private[parsing] trait FunctionParser {
  this: VitalDefinitionParser & StatementParser =>

  private def functionInclude[u: P]: P[Include[FunctionContents]] = {
    include[u, FunctionContents](functionDefinitions(_))
  }

  def funcInput[u: P]: P[Aggregation] = {
    P(Keywords.requires ~ aggregation)./
  }

  def funcOutput[u: P]: P[Aggregation] = {
    P(Keywords.returns ~ aggregation)./
  }

  private def functionDefinitions[u: P]: P[Seq[FunctionContents]] = {
    P(
      undefined(Seq.empty[FunctionContents]) | (
        vitalDefinitionContents | function | functionInclude | statement(
          StatementsSet.FunctionStatements
        )
      ).asInstanceOf[P[FunctionContents]]./.rep(0)
    )
  }

  private type BodyType = (Option[Aggregation], Option[Aggregation], Seq[FunctionContents])

  private def functionBody[u: P]: P[BodyType] =
    P(funcInput.? ~ funcOutput.? ~ functionDefinitions)

  /** Parses function literals, i.e.
    *
    * {{{
    *   function myFunction is {
    *     requires is Boolean
    *     returns is Integer
    *     statements | comments | functions | terms
    *   }
    * }}}
    */
  def function[u: P]: P[Function] = {
    P(
      Index ~ Keywords.function ~/ identifier ~ is ~ open ~/ functionBody ~ close ~ withMetaData ~/ Index
    )./.map { case (start, id, (ins, outs, contents), descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Function(at(start, end), id, ins, outs, contents.toContents, descriptives.toContents)
    }
  }
}
