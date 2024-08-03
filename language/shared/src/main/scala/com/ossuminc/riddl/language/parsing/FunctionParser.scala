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

/** Unit Tests For FunctionParser */
private[parsing] trait FunctionParser {
  this: ReferenceParser & TypeParser & StatementParser & CommonParser =>

  private def functionInclude[u: P]: P[IncludeHolder[OccursInFunction]] = {
    include[u, OccursInFunction](functionDefinitions(_))
  }

  def input[u: P]: P[Aggregation] = {
    P(Keywords.requires ~ Punctuation.colon.? ~ aggregation)./
  }

  def output[u: P]: P[Aggregation] = {
    P(Keywords.returns ~ Punctuation.colon.? ~ aggregation)./
  }

  private def statementBlock[u: P]: P[Seq[Statement]] = {
    P(
      Keywords.body./ ~ (undefined(Seq.empty[Statement]) | pseudoCodeBlock(StatementsSet.FunctionStatements))
    )
  }

  private def functionDefinitions[u: P]: P[Seq[OccursInFunction]] = {
    P(
      typeDef | function | term | functionInclude | authorRef | comment
    )./.rep(0)
  }

  private def functionBody[u: P]: P[
    (
      Option[Aggregation],
      Option[Aggregation],
      Seq[OccursInFunction],
      Seq[Statement]
    )
  ] = {
    P(undefined(None).map { _ =>
      (None, None, Seq.empty[OccursInFunction], Seq.empty[Statement])
    } | (input.? ~ output.? ~ functionDefinitions ~ statementBlock))
  }

  /** Parses function literals, i.e.
    *
    * {{{
    *   function myFunction is {
    *     requires is Boolean
    *     returns is Integer
    *     body { statements }
    *   }
    * }}}
    */
  def function[u: P]: P[Function] = {
    P(
      location ~ Keywords.function ~/ identifier ~ is ~ open ~/ functionBody ~/ close ~/ briefly ~/ description
    )./.map { case (loc, id, (ins, outs, contents, statements), briefly, description) =>
      Function(loc, id, ins, outs, contents, statements, briefly, description)
    }
  }
}
