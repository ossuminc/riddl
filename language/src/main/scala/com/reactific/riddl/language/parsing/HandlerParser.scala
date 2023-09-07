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

private[parsing] trait HandlerParser extends StatementParser with CommonParser {

  private def onClauseBody[u: P](set: StatementsSet): P[Seq[Statement]] = {
    P(
      open ~ (
        undefined(Seq.empty[Statement]) |
          setOfStatements(set)
        ) ~ close
    )
  }

  private def onOtherClause[u: P](set: StatementsSet): P[OnClause] = {
    P(
      Keywords.on ~ Keywords.other ~/ location ~ is ~ onClauseBody(set) ~ briefly ~
        description
    ).map(t => (OnOtherClause.apply _).tupled(t))
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnClause] = {
    P(
      Keywords.on ~ Keywords.init ~/ location ~ is ~ onClauseBody(set) ~ briefly ~
        description
    ).map(t => (OnInitClause.apply _).tupled(t))
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  private def onMessageClause[u: P](set: StatementsSet): P[OnClause] = {
    Keywords.on ~ location ~ messageRef ~/
      (Readability.from./ ~ messageOrigins).? ~ is ~ onClauseBody(set) ~
      briefly ~ description
  }.map(t => (OnMessageClause.apply _).tupled(t))

  private def onTermClause[u: P](set: StatementsSet): P[OnClause] = {
    P(
      Keywords.on ~ Keywords.term ~/ location ~ is ~ onClauseBody(set) ~ briefly ~
        description
    ).map(t => (OnInitClause.apply _).tupled(t))
  }

  private def handlerDefinitions[u: P](set: StatementsSet): P[Seq[OnClause]] = {
    P(onInitClause(set) | onMessageClause(set) | onTermClause(set) | onOtherClause(set)).rep(0)
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[OnClause]] = {
    undefined(Seq.empty[OnClause]) | handlerDefinitions(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ authorRefs ~ is ~ open ~
        handlerBody(set) ~
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

  def handlers[u: P](set: StatementsSet): P[Seq[Handler]] = handler(set).rep(0)
}
