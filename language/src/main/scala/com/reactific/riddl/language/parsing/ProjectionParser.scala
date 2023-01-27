/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait ProjectionParser extends TypeParser with HandlerParser {

  def projectionOptions[u: P]: P[Seq[ProjectionOption]] = {
    options[u, ProjectionOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ProjectionTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def projectionInclude[u: P]: P[Include[ProjectionDefinition]] = {
    include[ProjectionDefinition, u](projectionDefinitions(_))
  }

  def projectionDefinitions[u: P]: P[Seq[ProjectionDefinition]] = {
    P(term | projectionInclude | handler | invariant).rep(0)
  }

  type ProjectionBody =
    (Seq[ProjectionOption], Seq[Type], Seq[ProjectionDefinition])
  def projectionBody[u: P]: P[ProjectionBody] = {
    P(
      undefined(
        (Seq.empty[ProjectionOption], Seq.empty[Type], Seq.empty[ProjectionDefinition])
      ) | (projectionOptions ~ typeDef.rep(0) ~ projectionDefinitions)
    )
  }

  /** Parses projection definitions, e.g.
    *
    * {{{
    *   projection myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projection[u: P]: P[Projection] = {
    P(
      location ~ Keywords.projection ~/ identifier ~ authorRefs ~ is ~ open ~
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
        val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
        val includes = mapTo[Include[ProjectionDefinition]](groups.get(
          classOf[Include[ProjectionDefinition]]
        ))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        Projection(
          loc,
          id,
          authors,
          options,
          includes,
          types,
          handlers,
          invariants,
          terms,
          briefly,
          description
        )
    }
  }
}
