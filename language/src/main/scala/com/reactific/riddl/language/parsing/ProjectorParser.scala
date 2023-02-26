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
private[parsing] trait ProjectorParser
    extends TypeParser
    with HandlerParser
    with StreamingParser {

  private def projectionOptions[u: P]: P[Seq[ProjectionOption]] = {
    options[u, ProjectionOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ProjectionTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  private def projectionInclude[u: P]: P[Include[ProjectorDefinition]] = {
    include[ProjectorDefinition, u](projectionDefinitions(_))
  }

  private def projectionDefinitions[u: P]: P[Seq[ProjectorDefinition]] = {
    P(
      term | projectionInclude | handler | inlet | outlet | invariant |
        constant | typeDef
    ).rep(0)
  }

  private type ProjectionBody =
    (Seq[ProjectionOption], Seq[Type], Seq[ProjectorDefinition])

  private def projectionBody[u: P]: P[ProjectionBody] = {
    P(
      undefined(
        (
          Seq.empty[ProjectionOption],
          Seq.empty[Type],
          Seq.empty[ProjectorDefinition]
        )
      ) | (projectionOptions ~ typeDef.rep(0) ~ projectionDefinitions)
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
        projectionBody ~ close ~ briefly ~ description
    ).map {
      case (
            loc,
            id,
            authors,
            (options, types, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val handlers = mapTo[Handler](groups.get(classOf[Handler]))
        val constants = mapTo[Constant](groups.get(classOf[Constant]))
        val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
        val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
        val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
        val includes = mapTo[Include[ProjectorDefinition]](
          groups.get(
            classOf[Include[ProjectorDefinition]]
          )
        )
        val terms = mapTo[Term](groups.get(classOf[Term]))
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
          invariants,
          terms,
          briefly,
          description
        )
    }
  }
}
