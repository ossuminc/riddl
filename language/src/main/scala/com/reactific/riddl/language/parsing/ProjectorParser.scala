/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** Unit Tests For FunctionParser */
private[parsing] trait ProjectorParser {
  this: FunctionParser
    with HandlerParser
    with ReferenceParser
    with StatementParser
    with StreamingParser
    with TypeParser =>

  private def projectionOptions[u: P]: P[Seq[ProjectorOption]] = {
    options[u, ProjectorOption](StringIn(RiddlOption.technology).!) { case (loc, RiddlOption.technology, args) =>
      ProjectorTechnologyOption(loc, args)
    }
  }

  private def projectionInclude[u: P]: P[Include[ProjectorDefinition]] = {
    include[ProjectorDefinition, u](projectionDefinitions(_))
  }

  private def projectionDefinitions[u: P]: P[Seq[ProjectorDefinition]] = {
    P(
      typeDef | term | projectionInclude | handler(StatementsSet.ProjectorStatements) |
        function | inlet | outlet | invariant | constant | typeDef
    )./.rep(1)
  }

  private def projectionBody[u: P]: P[Seq[ProjectorDefinition]] = {
    P(
      undefined(Seq.empty[ProjectorDefinition]) | projectionDefinitions
    )
  }

  /** Parses projector definitions, e.g.
    *
    * {{{
    *   projector myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projector[u: P]: P[Projector] = {
    P(
      location ~ Keywords.projector ~/ identifier ~ authorRefs ~ is ~ open ~
        projectionOptions ~ projectionBody ~ close ~ briefly ~ description ~ endOfLineComment
    ).map {
      case (
            loc,
            id,
            authors,
            options,
            definitions,
            brief,
            description,
            comment
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val types = mapTo[Type](groups.get(classOf[Type]))
        val handlers = mapTo[Handler](groups.get(classOf[Handler]))
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val constants = mapTo[Constant](groups.get(classOf[Constant]))
        val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
        val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
        val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include[ProjectorDefinition]](
          groups.get(
            classOf[Include[ProjectorDefinition]]
          )
        )
        Projector(
          loc,
          id,
          authors,
          options,
          includes,
          types,
          constants,
          inlets,
          outlets,
          handlers,
          functions,
          invariants,
          terms,
          brief,
          description,
          comment
        )
    }
  }
}
