/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** StatementParser Define actions that various constructs can take for modelling behavior in a message-passing system
  */
private[parsing] trait StatementParser {
  this: ReferenceParser with CommonParser =>

  private def arbitraryStatement[u: P]: P[ArbitraryStatement] = {
    P(location ~ literalString).map(t => (ArbitraryStatement.apply _).tupled(t))
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(
      location ~ Keywords.error ~/ literalString
    )./.map { tpl => (ErrorStatement.apply _).tupled(tpl) }
  }

  private def setStatement[u: P]: P[SetStatement] = {
    P(
      location ~ Keywords.set ~/ fieldRef ~/ Readability.to ~ literalString
    )./.map { tpl => (SetStatement.apply _).tupled(tpl) }
  }

  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      location ~ Keywords.send ~/ messageRef ~/
        Readability.to ~ (outletRef | inletRef)
    )./.map { t => (SendStatement.apply _).tupled(t) }
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      location ~ Keywords.tell ~/ messageRef ~/ Readability.to ~ processorRef
    )./.map { t => (TellStatement.apply _).tupled(t) }
  }

  private def forEachStatement[u: P](set: StatementsSet): P[ForEachStatement] = {
    P(
      location ~ Keywords.foreach ~/ pathIdentifier ~ Keywords.do_ ~/ pseudoCodeBlock(set) ~ Keywords.end_
    )./.map { case (loc, pid, statements) =>
      ForEachStatement(loc, pid, statements)
    }
  }

  private def ifThenElseStatement[u: P](set: StatementsSet): P[IfThenElseStatement] = {
    P(
      location ~ Keywords.if_ ~/ literalString ~ Keywords.then_ ~/ pseudoCodeBlock(set) ~ (
        Keywords.else_ ~ pseudoCodeBlock(set)
      ).?
    )./.map { case (loc, cond, thens, maybeElses) =>
      val elses = maybeElses.getOrElse(Seq.empty[Statement])
      IfThenElseStatement(loc, cond, thens, elses)
    }
  }

  private def callStatement[u: P]: P[CallStatement] = {
    P(location ~ Keywords.call ~/ functionRef)./.map { tpl => (CallStatement.apply _).tupled(tpl) }
  }

  private def stopStatement[u: P]: P[StopStatement] = {
    P(
      location ~ Keywords.stop
    )./.map { (loc: At) => StopStatement(loc) }
  }

  private def anyDefStatements[u: P](set: StatementsSet): P[Statement] = {
    P(
      sendStatement | arbitraryStatement | errorStatement | setStatement | tellStatement | callStatement |
        stopStatement | ifThenElseStatement(set) | forEachStatement(set)
    )
  }

  enum StatementsSet:
    case AdaptorStatements,
      ApplicationStatements,
      ContextStatements,
      EntityStatements,
      FunctionStatements,
      ProjectorStatements,
      RepositoryStatements,
      SagaStatements,
      StreamStatements
  end StatementsSet

  private def morphStatement[u: P]: P[MorphStatement] = {
    P(
      location ~ Keywords.morph ~/ entityRef ~/ Readability.to ~ stateRef ~/ Readability.with_ ~ messageRef
    )./.map { tpl => (MorphStatement.apply _).tupled(tpl) }
  }

  private def becomeStatement[u: P]: P[BecomeStatement] = {
    P(
      location ~ Keywords.become ~/ entityRef ~ Readability.to ~ handlerRef
    )./.map { tpl => (BecomeStatement.apply _).tupled(tpl) }
  }

  private def replyStatement[u: P]: P[ReplyStatement] = {
    P(
      location ~ Keywords.reply ~/ Readability.with_.?./ ~ messageRef
    )./.map { tpl => (ReplyStatement.apply _).tupled(tpl) }
  }

  private def returnStatement[u: P]: P[ReturnStatement] = {
    P(
      location ~ Keywords.return_ ~ literalString
    )./.map(t => (ReturnStatement.apply _).tupled(t))
  }

  def statement[u: P](set: StatementsSet): P[Statement] = {
    set match {
      case StatementsSet.AdaptorStatements     => anyDefStatements(set) | replyStatement
      case StatementsSet.ApplicationStatements => anyDefStatements(set) | replyStatement
      case StatementsSet.ContextStatements     => anyDefStatements(set) | replyStatement
      case StatementsSet.EntityStatements =>
        anyDefStatements(set) | morphStatement | becomeStatement | replyStatement
      case StatementsSet.FunctionStatements   => anyDefStatements(set) | returnStatement
      case StatementsSet.ProjectorStatements  => anyDefStatements(set) | replyStatement
      case StatementsSet.RepositoryStatements => anyDefStatements(set) | replyStatement
      case StatementsSet.SagaStatements       => anyDefStatements(set) | returnStatement
      case StatementsSet.StreamStatements     => anyDefStatements(set)
    }
  }

  def setOfStatements[u: P](set: StatementsSet): P[Seq[Statement]] = {
    P(statement(set).rep(0))./
  }

  def pseudoCodeBlock[u: P](set: StatementsSet): P[Seq[Statement]] = {
    P(
      open ~/ (
        undefined(Seq.empty[Statement]) | statement(set).rep(1)
      ) ~ close./
    )
  }

  def invariant[u: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ (
        undefined(Option.empty[LiteralString]) | literalString.map(Some(_))
      ) ~ briefly ~ description ~ comments
    ).map { case (loc, id, condition, brief, description, comments) =>
      Invariant(loc, id, condition, brief, description, comments)
    }
  }

}
