/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait HandlerParser extends GherkinParser with FunctionParser {

  def onClauseBody[u: P]: P[Seq[Example]] = {
    open ~
      ((location ~ exampleBody).map { case (l, (g, w, t, b)) =>
        Seq(Example(l, Identifier(l, ""), g, w, t, b))
      } | nonEmptyExamples | undefined(Seq.empty[Example])) ~ close
  }

  def onClause[u: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ onClauseBody ~ briefly ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def handlerOptions[u: P]: P[Seq[HandlerOption]] = {
    options[u, HandlerOption](StringIn("partial").!) {
      case (loc, "partial", _) => PartialHandlerOption(loc)
      case (_, _, _)           => throw new RuntimeException("Impossible case")
    }
  }

  def handlerInclude[x: P]: P[Include[HandlerDefinition]] = {
    include[HandlerDefinition, x](handlerDefinitions(_))
  }

  def handlerDefinitions[u: P]: P[Seq[HandlerDefinition]] = {
    P(onClause | term | author | handlerInclude).rep(0)
  }

  def handlerBody[u: P]: P[(Seq[HandlerOption], Seq[HandlerDefinition])] = {
    undefined((Seq.empty[HandlerOption], Seq.empty[HandlerDefinition]))
    |
    (handlerOptions ~ handlerDefinitions)
  }

  def handler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~ open ~ handlerBody ~
        close ~ briefly ~ description
    ).map { case (loc, id, (options, definitions), briefly, description) =>
      val groups = definitions.groupBy(_.getClass)
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val includes = mapTo[Include[HandlerDefinition]](groups.get(
        classOf[Include[HandlerDefinition]]
      ))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val clauses = mapTo[OnClause](groups.get(classOf[OnClause]))

      Handler(
        loc,
        id,
        clauses,
        authors,
        includes,
        options,
        terms,
        briefly,
        description
      )
    }
  }

  def handlers[u: P]: P[Seq[Handler]] = handler.rep(0)
}
