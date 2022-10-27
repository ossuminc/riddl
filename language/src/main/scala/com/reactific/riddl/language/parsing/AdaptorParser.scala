/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser extends HandlerParser with GherkinParser with ActionParser {

  def adaptorOptions[u: P]: P[Seq[AdaptorOption]] = {
    options[u, AdaptorOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) => AdaptorTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def adaptorInclude[u: P]: P[Include[AdaptorDefinition]] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      (handler | adaptorInclude | term | author).rep(1) |
        undefined(Seq.empty[AdaptorDefinition])
    )
  }

  def adaptorDirection[u: P]: P[AdaptorDirection] = {
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
      location ~ Keywords.adaptor ~/ identifier ~ adaptorDirection ~
        contextRef ~ is ~ open ~ adaptorOptions ~ adaptorDefinitions ~ close ~
        briefly ~ description
    ).map { case (loc, id, dir, cref, options, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val includes = mapTo[Include[AdaptorDefinition]](groups.get(
        classOf[Include[AdaptorDefinition]]
      ))
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val handlers: Seq[Handler] = defs.filter(_.isInstanceOf[Handler])
        .map(_.asInstanceOf[Handler])
      Adaptor(
        loc,
        id,
        dir,
        cref,
        handlers,
        includes,
        authors,
        options,
        terms,
        briefly,
        description
      )
    }
  }
}
