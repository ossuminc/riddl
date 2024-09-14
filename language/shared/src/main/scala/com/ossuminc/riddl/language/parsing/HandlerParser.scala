/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait HandlerParser extends CommonParser with ReferenceParser with StatementParser {

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      location ~ Keywords.onOther ~ is ~/ pseudoCodeBlock(set) ~ withDescriptives
    )./map { case (loc, statements, descriptives) =>
      OnOtherClause(loc, statements.toContents, descriptives.toContents)
    }
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      location ~ Keywords.onInit ~ is ~/ pseudoCodeBlock(set) ~ withDescriptives
    ).map { case (loc, statements, descriptives) =>
      OnInitializationClause(loc, statements.toContents, descriptives.toContents)
    }
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      location ~ Keywords.onTerm ~ is ~/ pseudoCodeBlock(set) ~ withDescriptives
    ).map { case (loc, statements, descriptives) =>
      OnTerminationClause(loc, statements.toContents, descriptives.toContents)
    }
  }

  private def maybeName[u: P]: P[Option[Identifier]] = {
    P((identifier ~ Punctuation.colon).?)
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  private def onMessageClause[u: P](set: StatementsSet): P[OnMessageClause] = {
    location ~ Keywords.on ~ messageRef ~
      (from ~ maybeName ~~ messageOrigins).? ~ is ~/ pseudoCodeBlock(set) ~ withDescriptives
  }.map { case (loc, msgRef, msgOrigins, statements, descriptives) =>
    OnMessageClause(loc, msgRef, msgOrigins, statements.toContents, descriptives.toContents)
  }

  private def onClauses[u: P](set: StatementsSet): P[OnClause] = {
    P(onInitClause(set) | onOtherClause(set) | onTermClause(set) | onMessageClause(set) )
  }

  private def handlerContents[u:P](set: StatementsSet): P[Seq[HandlerContents]] = {
    (onClauses(set) | comment)./.rep(0).asInstanceOf[P[Seq[HandlerContents]]]
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[HandlerContents]] = {
    undefined(Seq.empty[HandlerContents]) | handlerContents(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~ open ~ handlerBody(set) ~ close ~ withDescriptives
    )./.map { case (loc, id, clauses, descriptives) =>
      Handler(loc, id, clauses.toContents, descriptives.toContents)
    }
  }

  def handlers[u: P](set: StatementsSet): P[Seq[Handler]] = handler(set).rep(0)

}
