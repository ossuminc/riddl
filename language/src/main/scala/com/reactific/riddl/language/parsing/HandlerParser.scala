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

private[parsing] trait HandlerParser extends FunctionParser {

  private def onClauseBody[u: P]: P[Seq[Action]] = {
    open ~ actions ~ close
  }

  private def onOtherClause[u: P]: P[OnClause] = {
    P(
      Keywords.on ~ Keywords.other ~/ location ~ is ~ onClauseBody ~ briefly ~
        description
    ).map(t => (OnOtherClause.apply _).tupled(t))
  }

  private def onInitClause[u: P]: P[OnClause] = {
    P(
      Keywords.on ~ Keywords.init ~/ location ~ is ~ onClauseBody ~ briefly ~
        description
    ).map(t => (OnInitClause.apply _).tupled(t))
  }

  private def messageOrigins[u:P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef )
  }
  private def onMessageClause[u: P]: P[OnClause] = {
    Keywords.on ~ location ~ messageRef ~/
      (Readability.from./ ~ messageOrigins).? ~ is ~ onClauseBody ~
      briefly ~ description
  }.map(t => (OnMessageClause.apply _).tupled(t))

  private def onTermClause[u: P]: P[OnClause] = {
    P(
      Keywords.on ~ Keywords.term ~/ location ~ is ~ onClauseBody ~ briefly ~
        description
    ).map(t => (OnInitClause.apply _).tupled(t))
  }

  private def handlerDefinitions[u: P]: P[Seq[OnClause]] = {
    P(onInitClause | onMessageClause | onTermClause | onOtherClause).rep(0)
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
