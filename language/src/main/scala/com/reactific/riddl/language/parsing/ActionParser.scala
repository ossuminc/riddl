/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.parsing.Terminals.*
import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** ActionParser Define actions that various constructs can take for modelling
  * behavior in a message-passing system
  */
private[parsing] trait ActionParser
    extends ReferenceParser
    with ExpressionParser {

  private def arbitraryAction[u: P]: P[ArbitraryAction] = {
    P(location ~ literalString).map { tpl =>
      (ArbitraryAction.apply _).tupled(tpl)
    }
  }

  private def errorAction[u: P]: P[ErrorAction] = {
    P(location ~ Keywords.error ~ literalString).map { tpl =>
      (ErrorAction.apply _).tupled(tpl)
    }
  }

  private def assignAction[u: P]: P[AssignAction] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Readability.to ~ expression
    ).map { t => (AssignAction.apply _).tupled(t) }
  }

  private def appendAction[u: P]: P[AppendAction] = {
    P(
      location ~ Keywords.append ~/ expression ~ Readability.to ~ pathIdentifier
    ).map { t => (AppendAction.apply _).tupled(t) }
  }

  private def becomeAction[u: P]: P[BecomeAction] = {
    P(
      Keywords.become ~/ location ~ entityRef ~ Readability.to ~ handlerRef
    ).map { tpl => (BecomeAction.apply _).tupled(tpl) }
  }

  def messageConstructor[u: P]: P[MessageConstructor] = {
    P(location ~ messageRef ~ argList.?).map { case (loc, ref, args) =>
      args match {
        case Some(args) => MessageConstructor(loc, ref, args)
        case None       => MessageConstructor(loc, ref)
      }
    }
  }

  private def returnAction[u: P]: P[ReturnAction] = {
    P(Keywords.return_ ~/ location ~ expression)
      .map(t => (ReturnAction.apply _).tupled(t))
  }

  private def sendAction[u: P]: P[SendAction] = {
    P(
      Keywords.send ~/ location ~ messageConstructor ~
        Readability.to ~ (outletRef | inletRef)
    ).map { t => (SendAction.apply _).tupled(t) }
  }

  private def functionCallAction[u: P]: P[FunctionCallAction] = {
    P(Keywords.call ~/ location ~ pathIdentifier ~ argList)
      .map(tpl => (FunctionCallAction.apply _).tupled(tpl))
  }

  private def compoundAction[u: P]: P[CompoundAction] = {
    P(location ~ open ~ allActions.rep(1, ",") ~ close)
      .map(tpl => (CompoundAction.apply _).tupled(tpl))
  }

  def entityActions[u: P]: P[EntityAction] = {
    P(assignAction | appendAction | becomeAction)
  }

  def functionActions[u:P]: P[FunctionAction] = {
    P(returnAction)
  }

  def anyActions[u:P]: P[AnyAction] = {
    P(sendAction | arbitraryAction | errorAction | functionCallAction |
      compoundAction)
  }

  def allActions[u: P]: P[Action] = {
    P(
      entityActions | functionActions | anyActions
    )
  }

}
