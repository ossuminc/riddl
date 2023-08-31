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
private[parsing] trait StatementParser extends ReferenceParser with ConditionParser with CommonParser {

  private def label[u: P]: P[Identifier] = {
    P((identifier ~ Punctuation.colon).?).map(_.getOrElse(Identifier.empty))
  }

  private def arbitraryStatement[u: P]: P[ArbitraryStatement] = {
    P(location ~ label ~ literalString).map(t => (ArbitraryStatement.apply _).tupled(t))
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(
      location ~ label ~ Keywords.error ~ literalString
    )./.map { tpl =>
      (ErrorStatement.apply _).tupled(tpl)
    }
  }

  private def morphStatement[u: P]: P[MorphStatement] = {
    P(
      location ~ label ~ Keywords.morph ~/ entityRef ~/ Readability.to ~ stateRef ~ value
    )./.map { tpl => (MorphStatement.apply _).tupled(tpl) }
  }

  private def becomeStatement[u: P]: P[BecomeStatement] = {
    P(
      location ~ label ~ Keywords.become ~/ entityRef ~ Readability.to ~ handlerRef
    )./.map { tpl => (BecomeStatement.apply _).tupled(tpl) }
  }

  private def arguments[u: P]: P[ArgumentValues] = {
    P(
      location ~
        Punctuation.roundOpen ~
        (identifier ~ Punctuation.equalsSign ~ value)
          .rep(0, Punctuation.comma)
        ~ Punctuation.roundClose
    )./.map { case (loc, args) =>
      val map = HashMap.from[Identifier, Value](args)
      ArgumentValues(loc, map)
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
      location ~ label ~ Keywords.return_ ~/ value
    )./.map(t => (ReturnStatement.apply _).tupled(t))
  }

  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      location ~ label ~ Keywords.send ~/ messageValue ~
        Readability.to ~ (outletRef | inletRef)
    )./.map { t => (SendStatement.apply _).tupled(t) }
  }

  private def functionCallStatement[u: P]: P[FunctionCallStatement] = {
    P(
      location ~ label ~ Keywords.call ~/ pathIdentifier ~ argumentValues
    )./.map(tpl => (FunctionCallStatement.apply _).tupled(tpl))
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      location ~ label ~ Keywords.tell ~/ messageValue ~ Readability.to.? ~
        processorRef
    )./.map { t => (TellStatement.apply _).tupled(t) }
  }

  private def setStatement[u: P]: P[SetStatement] = {
    P(
      location ~ label ~ Keywords.set ~/ fieldRef ~ Readability.to ~ value
    )./.map { case (loc, label, ref, value) =>
      SetStatement(loc, label, ref, value)
    }
  }

  private def ifStatement[u: P](set: StatementsSet): P[IfStatement] = {
    P(
      location ~ label ~ Keywords.if_ ~/ condition ~ Keywords.then_ ~/ setOfStatements(set) ~
        (Keywords.else_ ~/ setOfStatements(set)).?.map {
          case None    => Seq.empty[Statement]
          case Some(s) => s
        } ~ Keywords.end_
    )./.map { case (loc, label, cond, then_, else_) =>
      IfStatement(loc, label, cond, then_, else_)
    }
  }

  private def forEachStatement[u: P](set: StatementsSet): P[ForEachStatement] = {
    P(
      location ~ label ~ Keywords.foreach ~ pathIdentifier ~ Keywords.do_ ~
        setOfStatements(set) ~ Keywords.end_
    )./.map { case (loc, label, pid, statements) =>
      ForEachStatement(loc, label, pid, statements)
    }
  }

  private def anyDefStatements[u: P](set: StatementsSet): P[Statement] = {
    P(
      sendStatement | arbitraryStatement | errorStatement | functionCallStatement |
        tellStatement | ifStatement(set) | forEachStatement(set) |
        setStatement
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
      case StatementsSet.AdaptorStatements     => anyDefStatements(set)
      case StatementsSet.ApplicationStatements => anyDefStatements(set)
      case StatementsSet.ContextStatements     => anyDefStatements(set)
      case StatementsSet.EntityStatements =>
        anyDefStatements(set) | morphStatement | becomeStatement
      case StatementsSet.FunctionStatements =>
        anyDefStatements(set) | returnStatement
      case StatementsSet.ProjectorStatements  => anyDefStatements(set)
      case StatementsSet.RepositoryStatements => anyDefStatements(set)
      case StatementsSet.SagaStatements =>
        anyDefStatements(set) | returnStatement
      case StatementsSet.StreamStatements => anyDefStatements(set)
    }
  }

  def setOfStatements[u: P](set: StatementsSet): P[Seq[Statement]] = {
    P(statement(set).rep(0))./
  }
}
