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
    P(location ~ literalString ~ briefly ~ description)./.map { tpl =>
      (ArbitraryAction.apply _).tupled(tpl)
    }
  }

  private def errorAction[u: P]: P[ErrorAction] = {
    P(location ~ Keywords.error ~ literalString)./.map { tpl =>
      (ErrorAction.apply _).tupled(tpl)
    }
  }

  private def morphAction[u: P]: P[MorphAction] = {
    P(
      location ~ Keywords.morph ~/ entityRef ~/ Readability.to ~ stateRef ~/
        Readability.with_ ~ expression
    )./.map { tpl => (MorphAction.apply _).tupled(tpl) }
  }

  private def becomeAction[u: P]: P[BecomeAction] = {
    P(
      location ~ Keywords.become ~/ entityRef ~ Readability.to ~ handlerRef
    )./.map { tpl => (BecomeAction.apply _).tupled(tpl) }
  }

  def messageConstructor[u: P]: P[MessageConstructor] = {
    P(location ~ messageRef ~ argList.?)./.map { case (loc, ref, args) =>
      args match {
        case Some(args) => MessageConstructor(loc, ref, args)
        case None       => MessageConstructor(loc, ref)
      }
    }
  }

  private def returnAction[u: P]: P[ReturnAction] = {
    P(
      location ~ Keywords.return_ ~/ expression
    )./.map(t => (ReturnAction.apply _).tupled(t))
  }

  private def sendAction[u: P]: P[SendAction] = {
    P(
      location ~ Keywords.send ~/ messageConstructor ~
        Readability.to ~ (outletRef | inletRef)
    )./.map { t => (SendAction.apply _).tupled(t) }
  }

  private def functionCallAction[u: P]: P[FunctionCallAction] = {
    P(
      location ~ Keywords.call ~/ pathIdentifier ~ argList
    )./.map(tpl => (FunctionCallAction.apply _).tupled(tpl))
  }

  private def tellAction[u: P]: P[TellAction] = {
    P(
      location ~ Keywords.tell ~/ messageConstructor ~ Readability.to.? ~
        processorRef
    )./.map { t => (TellAction.apply _).tupled(t) }
  }

  private def compoundAction[u: P]: P[CompoundAction] = {
    P(location ~ open ~ allActions.rep(1, ",") ~ close)
      .map(tpl => (CompoundAction.apply _).tupled(tpl))
  }

  private def entityActions[u: P]: P[EntityAction] = {
    P(tellAction | morphAction | becomeAction)
  }

  private def functionActions[u: P]: P[FunctionAction] = {
    P(returnAction)
  }

  private def anyActions[u: P]: P[AnyAction] = {
    P(
      sendAction | arbitraryAction | errorAction | functionCallAction |
        compoundAction
    )
  }

  def allActions[u: P]: P[Action] = {
    P(
      entityActions | functionActions | anyActions
    )
  }

  def actions[u:P]: P[Seq[Action]] = {
    P(allActions.rep(0))
  }

}
