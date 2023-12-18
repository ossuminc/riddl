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

  this: ReferenceParser with TypeParser with StatementParser with CommonParser =>

  private def functionOptions[X: P]: P[Seq[FunctionOption]] = {
    options[X, FunctionOption](StringIn(RiddlOption.tail_recursive).!) { case (loc, RiddlOption.tail_recursive, _) =>
      TailRecursive(loc)
    }
  }

  private def functionInclude[x: P]: P[Include[FunctionValue]] = {
    include[FunctionValue, x](functionDefinitions(_))
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

  private def functionDefinitions[u: P]: P[Seq[FunctionValue]] = {
    P(
      typeDef | function | term | functionInclude
    )./.rep(0)
  }

  private def functionBody[u: P]: P[
    (
      Option[Aggregation],
      Option[Aggregation],
      Seq[FunctionValue],
      Seq[Statement]
    )
  ] = {
    P(
      undefined((None, None, Seq.empty[FunctionValue], Seq.empty[Statement])) |
        (input.? ~ output.? ~ functionDefinitions ~ statementBlock)
    )
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
      location ~ Keywords.function ~/ identifier ~ authorRefs ~ is ~ open ~
        functionOptions ~ functionBody ~ close ~ briefly ~ description ~ comments
    )./.map {
      case (
            loc,
            id,
            authors,
            options,
            (input, output, definitions, statements),
            brief,
            description,
            comments
          ) =>
        val contents: Seq[FunctionValue] = definitions ++ statements ++ authors ++ options
        input.map(_.fields).getOrElse(Seq.empty[Field]) ++ output.map(_.fields).getOrElse(Seq.empty[Field])
        Function(
          loc,
          id,
          input,
          output,
          contents,
          brief,
          description,
          comments
        )
    }
  }
}
