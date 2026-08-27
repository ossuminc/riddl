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

  /** `requires` is ORDINARY CONTENT, not a body prefix.
    *
    * It used to be parsed as `[func_input] [func_output] {definitions}`, a fixed prefix. Once a
    * comment became a legal definition (867ab0333) a comment above `requires` consumed the
    * definitions slot and `requires` was then rejected — so the effective rule became
    * "`requires`/`returns` must be the very first tokens of the body", which is exactly where a
    * reader wants a comment explaining them. Parsing them as content dissolves the prefix, so
    * comments may precede, separate or follow the two clauses freely.
    */
  def funcInput[u: P]: P[Requires] = {
    P(Index ~ Keywords.requires ~ requiresReturnsValue ~ Index)./.map { case (start, value, end) =>
      Requires(at(start, end), value)
    }
  }

  def funcOutput[u: P]: P[Returns] = {
    P(Index ~ Keywords.returns ~ requiresReturnsValue ~ Index)./.map { case (start, value, end) =>
      Returns(at(start, end), value)
    }
  }

  /** `???` is a CONTENT item here, not an alternative to the whole content list.
    *
    * Every other container spells its body `undefined | definitions`, which reads as "either the
    * body is unspecified or it has content". That was fine while `requires`/`returns` sat OUTSIDE
    * that choice as a prefix, because `requires X returns Y ???` — signature known, body not yet
    * written — still parsed. It is in the corpus (`everything_full.riddl:72`). Moving the clauses
    * into the content list would have made that shape a parse error, so `???` moves with them: it
    * is one more thing a body may contain, contributing nothing to the contents.
    *
    * This is the same dissolution the clauses themselves underwent, for the same reason — a fixed
    * body shape forced an ordering nobody asked for.
    */
  private def functionContent[u: P]: P[Seq[FunctionContents]] = {
    P(
      undefined(Seq.empty[FunctionContents]) | (
        vitalDefinitionContents | funcInput | funcOutput | function | statement(
          StatementsSet.FunctionStatements
        )
      ).asInstanceOf[P[FunctionContents]]./.map(Seq(_))
    )
  }

  private def functionBody[u: P]: P[Seq[FunctionContents]] =
    P(functionContent.rep(0).map(_.flatten))

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
    )./.map { case (start, id, contents, descriptives, end) =>
      checkRequiresReturnsCardinality(contents, "Function")
      Function(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
