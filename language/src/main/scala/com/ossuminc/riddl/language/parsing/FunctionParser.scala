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

/** Unit Tests For FunctionParser */
private[parsing] trait FunctionParser {
  this: VitalDefinitionParser & StatementParser =>

  // A9: `requires`/`returns` name a Type (preferred) or, deprecated, an inline Aggregation.
  // The two forms are disjoint by their leading token (`{` for aggregation, an identifier or
  // type keyword for typeRef), so a plain alternation suffices.
  private def requiresReturnsValue[u: P]: P[TypeRef | Aggregation] =
    P(aggregation.map(a => a: TypeRef | Aggregation) | typeRef.map(t => t: TypeRef | Aggregation))

  def funcInput[u: P]: P[TypeRef | Aggregation] = {
    P(Keywords.requires ~ requiresReturnsValue)./
  }

  def funcOutput[u: P]: P[TypeRef | Aggregation] = {
    P(Keywords.returns ~ requiresReturnsValue)./
  }

  private def functionDefinitions[u: P]: P[Seq[FunctionContents]] = {
    P(
      undefined(Seq.empty[FunctionContents]) | (
        vitalDefinitionContents | function | statement(
          StatementsSet.FunctionStatements
        )
      ).asInstanceOf[P[FunctionContents]]./.rep(0)
    )
  }

  private type BodyType =
    (Option[TypeRef | Aggregation], Option[TypeRef | Aggregation], Seq[FunctionContents])

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
      Function(at(start, end), id, ins, outs, contents.toContents, descriptives.toContents)
    }
  }
}
