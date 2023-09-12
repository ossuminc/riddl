/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*
import Terminals.*

/** Unit Tests For FunctionParser */
private[parsing] trait FunctionParser extends ReferenceParser with TypeParser with CommonParser {

  private def functionOptions[X: P]: P[Seq[FunctionOption]] = {
    options[X, FunctionOption](StringIn(Options.tail_recursive).!) {
      case (loc, Options.tail_recursive, _) => TailRecursive(loc)
      case (_, _, _)                        => throw new RuntimeException("Impossible case")
    }
  }

  private def functionInclude[x: P]: P[Include[FunctionDefinition]] = {
    include[FunctionDefinition, x](functionDefinitions(_))
  }

  def input[u: P]: P[Aggregation] = {
    P(Keywords.requires ~ Punctuation.colon.? ~ aggregation)
  }

  def output[u: P]: P[Aggregation] = {
    P(Keywords.returns ~ Punctuation.colon.? ~ aggregation)
  }

  private def functionDefinitions[u: P]: P[Seq[FunctionDefinition]] = {
    P(
      typeDef | function | term | functionInclude
    ).rep(0)
  }

  private def statementBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      Keywords.body ~ (
        undefined(Seq.empty[LiteralString]) |
          open ~ markdownLines ~ close
      )
    )
  }

  private def functionBody[u: P]: P[
    (
      Seq[FunctionOption],
      Option[Aggregation],
      Option[Aggregation],
      Seq[FunctionDefinition],
      Seq[LiteralString]
    )
  ] = {
    P(undefined(None).map { _ =>
      (Seq.empty[FunctionOption], None, None, Seq.empty[FunctionDefinition], Seq.empty[LiteralString])
    } | (functionOptions ~ input.? ~ output.? ~ functionDefinitions ~ statementBlock))
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
        functionBody ~ close ~ briefly ~ description
    ).map {
      case (
            loc,
            id,
            authors,
            (options, input, output, definitions, statements),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val types = mapTo[Type](groups.get(classOf[Type]))
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include[FunctionDefinition]](
          groups.get(
            classOf[Include[FunctionDefinition]]
          )
        )
        Function(
          loc,
          id,
          input,
          output,
          types,
          functions,
          statements,
          authors,
          includes,
          options,
          terms,
          briefly,
          description
        )
    }
  }
}
