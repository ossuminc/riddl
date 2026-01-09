/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait HandlerParser extends CommonParser with ReferenceParser with StatementParser {

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      Index ~ Keywords.onOther ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    )./ map { case (start, statements, descriptives, end) =>
      OnOtherClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      Index ~ Keywords.onInit ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnInitializationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      Index ~ Keywords.onTerm ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
    ).map { case (start, statements, descriptives, end) =>
      OnTerminationClause(at(start, end), statements.toContents, descriptives.toContents)
    }
  }

  private def maybeName[u: P]: P[Option[Identifier]] = {
    P((identifier ~ Punctuation.colon).?)
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  private def onMessageClause[u: P](set: StatementsSet): P[OnMessageClause] = {
    Index ~ Keywords.on ~ messageRef ~
      (from ~ maybeName ~~ messageOrigins).? ~ is ~/ pseudoCodeBlock(set) ~ withMetaData ~/ Index
  }.map { case (start, msgRef, msgOrigins, statements, descriptives, end) =>
    OnMessageClause(at(start, end), msgRef, msgOrigins, statements.toContents, descriptives.toContents)
  }

  def onClause[u: P](set: StatementsSet): P[OnClause] = {
    P(onInitClause(set) | onOtherClause(set) | onTermClause(set) | onMessageClause(set))
  }

  private def handlerContents[u: P](set: StatementsSet): P[Seq[HandlerContents]] = {
    (onClause(set) | comment)./.rep(0).asInstanceOf[P[Seq[HandlerContents]]]
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[HandlerContents]] = {
    undefined(Seq.empty[HandlerContents]) | handlerContents(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Index ~ Keywords.handler ~/ identifier ~ is ~ open ~ handlerBody(set) ~ close ~ withMetaData ~/ Index
    )./.map { case (start, id, clauses, descriptives, end) =>
      Handler(at(start, end), id, clauses.toContents, descriptives.toContents)
    }
  }

  def handlers[u: P](set: StatementsSet): P[Seq[Handler]] = handler(set).rep(0)

}
