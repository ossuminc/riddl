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

private[parsing] trait HandlerParser {
  this: ReferenceParser with StatementParser with CommonParser =>

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      location ~ Keywords.on ~ Keywords.other ~ is ~/ pseudoCodeBlock(set) ~ briefly ~
        description
    ).map(t => (OnOtherClause.apply _).tupled(t))
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitClause] = {
    P(
      location ~ Keywords.on ~ Keywords.init ~ is ~/ pseudoCodeBlock(set) ~ briefly ~
        description
    ).map(t => (OnInitClause.apply _).tupled(t))
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      location ~ Keywords.on ~ Keywords.term ~ is ~/ pseudoCodeBlock(set) ~
        briefly ~ description
    ).map(t => (OnTerminationClause.apply _).tupled(t))
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  private def onMessageClause[u: P](set: StatementsSet): P[OnMessageClause] = {
    location ~ Keywords.on ~/ messageRef ~
      (Readability.from./ ~ messageOrigins).? ~ is ~/ pseudoCodeBlock(set) ~
      briefly ~ description
  }.map(t => (OnMessageClause.apply _).tupled(t))

  private def onClauses[u: P](set: StatementsSet): P[Seq[OnClause]] = {
    P(onInitClause(set) | onOtherClause(set) | onTermClause(set) | onMessageClause(set)).rep(0)
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[OnClause]] = {
    undefined(Seq.empty[OnClause]) | onClauses(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ authorRefs ~ is ~ open ~
        handlerBody(set) ~
        close ~ briefly ~ description
    )./.map { case (loc, id, authors, clauses, briefly, desc) =>
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
