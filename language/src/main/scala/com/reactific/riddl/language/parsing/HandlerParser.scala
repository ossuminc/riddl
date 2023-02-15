/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

private[parsing] trait HandlerParser extends GherkinParser with FunctionParser {

  private def onClauseBody[u: P]: P[Seq[Example]] = {
    open ~
      ((location ~ exampleBody).map { case (l, (g, w, t, b)) =>
        Seq(Example(l, Identifier(l, ""), g, w, t, b))
      } | nonEmptyExamples | undefined(Seq.empty[Example])) ~ close
  }

  private def fromClause[u: P]: P[Reference[Definition]] = {
    P(
      Readability.from./ ~
        (actorRef | entityRef | pipeRef | adaptorRef | contextRef)
    )
  }

  private def onOtherClause[u: P]: P[OnClause] = {
    P( Keywords.on ~ Keywords.other ~/ location ~ onClauseBody ~ briefly ~
      description ).map( t => (OnOtherClause.apply _).tupled(t))
  }

  private def onMessageClause[u: P]: P[OnClause] = {
    Keywords.on ~ location ~ messageRef ~/ fromClause.? ~ onClauseBody ~
      briefly ~ description
  }.map(t => (OnMessageClause.apply _).tupled(t))

  private def handlerDefinitions[u: P]: P[Seq[OnClause]] = {
    P(onMessageClause | onOtherClause).rep(0)
  }

  private def handlerBody[u: P]: P[Seq[OnClause]] = {
    undefined(Seq.empty[OnClause]) | handlerDefinitions
  }

  def handler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ authorRefs ~ is ~ open ~
        handlerBody ~
        close ~ briefly ~ description
    ).map { case (loc, id, authors, clauses, briefly, desc) =>
      Handler(
        loc,
        id,
        clauses,
        authors,
        briefly,
        desc
      )
    }
  }

  def handlers[u: P]: P[Seq[Handler]] = handler.rep(0)
}
