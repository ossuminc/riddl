/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import Terminals.*

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
private[parsing] trait AdaptorParser {
  this: HandlerParser
    with FunctionParser
    with StreamingParser
    with StatementParser
    with ReferenceParser
    with TypeParser =>

  private def adaptorOptions[u: P]: P[Seq[AdaptorOption]] = {
    options[u, AdaptorOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) => AdaptorTechnologyOption(loc, args)
    }
  }

  private def adaptorInclude[u: P]: P[Include[AdaptorDefinition]] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  private def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      (handler(StatementsSet.AdaptorStatements) | function | inlet |
        outlet | adaptorInclude | term | constant
      )./.rep(1)
    )
  }

  private def adaptorBody[u:P]: P[Seq[AdaptorDefinition]] = {
    undefined(Seq.empty[AdaptorDefinition])./ | adaptorDefinitions./
  }

  private def adaptorDirection[u: P]: P[AdaptorDirection] = {
    P(location ~ (Readability.from.! | Readability.to.!)).map {
      case (loc, "from") => InboundAdaptor(loc)
      case (loc, "to")   => OutboundAdaptor(loc)
      case (loc, _) =>
        error("Impossible condition at $loc")
        InboundAdaptor(loc)
    }
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ authorRefs ~
        adaptorDirection ~ contextRef ~ is ~ open ~ adaptorOptions ~
        adaptorBody ~ close ~ briefly ~ description
    ).map {
      case (
            loc,
            id,
            authorRefs,
            dir,
            cref,
            options,
            defs,
            briefly,
            description
          ) =>
        val groups = defs.groupBy(_.getClass)
        val includes = mapTo[Include[AdaptorDefinition]](
          groups.get(
            classOf[Include[AdaptorDefinition]]
          )
        )
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val handlers: Seq[Handler] = mapTo[Handler](groups.get(classOf[Handler]))
        val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
        val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
        val types = mapTo[Type](groups.get(classOf[Outlet]))
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val constants = mapTo[Constant](groups.get(classOf[Constant]))
        Adaptor(
          loc,
          id,
          dir,
          cref,
          handlers,
          inlets,
          outlets,
          types,
          constants,
          functions,
          includes,
          authorRefs,
          options,
          terms,
          briefly,
          description
        )
    }
  }
}
