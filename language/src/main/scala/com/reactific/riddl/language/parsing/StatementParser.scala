/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.parsing.Terminals.*
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.collection.immutable.HashMap

/** StatementParser Define actions that various constructs can take for modelling behavior in a message-passing system
  */
private[parsing] trait StatementParser extends ReferenceParser {

  private def arbitraryStatement[u: P]: P[ArbitraryStatement] = {
    P(location ~ literalString).map(t => (ArbitraryStatement.apply _).tupled(t))
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(location ~ Keywords.error ~ literalString)./.map { tpl =>
      (ErrorStatement.apply _).tupled(tpl)
    }
  }

  private def morphStatement[u: P]: P[MorphStatement] = {
    P(
      location ~ Keywords.morph ~/ entityRef ~/ Readability.to ~ stateRef
    )./.map { tpl => (MorphStatement.apply _).tupled(tpl) }
  }

  private def becomeStatement[u: P]: P[BecomeStatement] = {
    P(
      location ~ Keywords.become ~/ entityRef ~ Readability.to ~ handlerRef
    )./.map { tpl => (BecomeStatement.apply _).tupled(tpl) }
  }

  private def arguments[u: P]: P[Arguments] = {
    P(
      location ~
        Punctuation.curlyOpen ~
        (identifier ~ literalString).rep(0, Punctuation.comma)
        ~ Punctuation.curlyClose
    ).map { case (loc, args) =>
      val map = HashMap.from[Identifier, LiteralString](args)
      Arguments(loc, map)
    }
  }

  def messageValue[u: P]: P[MessageValue] = {
    P(location ~ messageRef ~ arguments.?)./.map { case (loc, ref, args) =>
      args match {
        case Some(args) => MessageValue(loc, ref, args)
        case None       => MessageValue(loc, ref)
      }
    }
  }

  private def returnStatement[u: P]: P[ReturnStatement] = {
    P(
      location ~ Keywords.return_ ~/ literalString
    )./.map(t => (ReturnStatement.apply _).tupled(t))
  }

  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      location ~ Keywords.send ~/ messageValue ~
        Readability.to ~ (outletRef | inletRef)
    )./.map { t => (SendStatement.apply _).tupled(t) }
  }

  private def functionCallStatement[u: P]: P[FunctionCallStatement] = {
    P(
      location ~ Keywords.call ~/ pathIdentifier ~ arguments
    )./.map(tpl => (FunctionCallStatement.apply _).tupled(tpl))
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      location ~ Keywords.tell ~/ messageValue ~ Readability.to.? ~
        processorRef
    )./.map { t => (TellStatement.apply _).tupled(t) }
  }

  private def anyDefStatements[u: P]: P[Statement] = {
    P(
      sendStatement | arbitraryStatement | errorStatement | functionCallStatement |
        tellStatement
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

  def statement[u: P](set: StatementsSet): P[Statement] = {
    set match {
      case StatementsSet.AdaptorStatements     => anyDefStatements
      case StatementsSet.ApplicationStatements => anyDefStatements
      case StatementsSet.ContextStatements     => anyDefStatements
      case StatementsSet.EntityStatements =>
        anyDefStatements | morphStatement | becomeStatement
      case StatementsSet.FunctionStatements =>
        anyDefStatements | returnStatement
      case StatementsSet.ProjectorStatements  => anyDefStatements
      case StatementsSet.RepositoryStatements => anyDefStatements
      case StatementsSet.SagaStatements =>
        anyDefStatements | returnStatement
      case StatementsSet.StreamStatements => anyDefStatements
    }
  }

  def setOfStatements[u:P](set: StatementsSet): P[Seq[Statement]] = {
    P(statement(set).rep(0))
  }
}
