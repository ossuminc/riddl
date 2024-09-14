/*
 * Copyright 2019 Ossum, Inc.
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

  def input[u: P]: P[Aggregation] = {
    P(Keywords.requires ~ Punctuation.colon.? ~ aggregation)./
  }

  def output[u: P]: P[Aggregation] = {
    P(Keywords.returns ~ Punctuation.colon.? ~ aggregation)./
  }

  private def functionDefinitions[u: P]: P[Seq[FunctionContents]] = {
    P(
      undefined(Seq.empty[FunctionContents]) | (
        vitalDefinitionContents | function | functionInclude | statement(StatementsSet.FunctionStatements)
      ).asInstanceOf[P[FunctionContents]]./.rep(0)
    )
  }

  private type BodyType = (Option[Aggregation], Option[Aggregation], Seq[FunctionContents])
  
  private def functionBody[u: P]: P[BodyType] =
    P(input.? ~ output.? ~ functionDefinitions)

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
      location ~ Keywords.function ~/ identifier ~ is ~ open ~/ functionBody ~ close ~ withDescriptives
    )./.map { case (loc, id, (ins, outs, contents), descriptives) =>
      checkForDuplicateIncludes(contents)
      Function(loc, id, ins, outs, contents.toContents, descriptives.toContents)
    }
  }
}
