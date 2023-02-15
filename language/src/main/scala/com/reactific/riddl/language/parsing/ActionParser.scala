/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

/** ActionParser Define actions that various constructs can take for modelling
  * behavior in a message-passing system
  */
private[parsing] trait ActionParser extends ReferenceParser with ExpressionParser {

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

  private def setAction[u: P]: P[SetAction] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Readability.to ~ expression
    ).map { t => (SetAction.apply _).tupled(t) }
  }

  private def appendAction[u: P]: P[AppendAction] = {
    P(
      location ~ Keywords.append ~/ expression ~ Readability.to ~ pathIdentifier
    ).map { t => (AppendAction.apply _).tupled(t) }
  }

  private def morphAction[u: P]: P[MorphAction] = {
    P(
      Keywords.morph ~/ location ~ entityRef ~ Readability.to.? ~ stateRef
    ).map { tpl => (MorphAction.apply _).tupled(tpl) }
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
    P(Keywords.return_ ~/ location ~ expression )
      .map(t => (ReturnAction.apply _).tupled(t))
  }

  private def yieldAction[u: P]: P[YieldAction] = {
    P(Keywords.yield_ ~/ location ~ messageConstructor)
      .map(t => (YieldAction.apply _).tupled(t))
  }

  private def publishAction[u: P]: P[PublishAction] = {
    P(
      Keywords.publish ~/ location ~ messageConstructor ~ Readability.to ~ pipeRef
    ).map { t => (PublishAction.apply _).tupled(t) }
  }

  private def subscribeAction[u: P]: P[Action] = {
    P(Keywords.subscribe ~/ location ~ Readability.to ~ pipeRef ~ Readability.for_ ~ typeRef).map {
      t => (SubscribeAction.apply _).tupled(t)
    }
  }

  private def functionCallAction[u: P]: P[FunctionCallAction] = {
    P(Keywords.call ~/ location ~ pathIdentifier ~ argList)
      .map(tpl => (FunctionCallAction.apply _).tupled(tpl))
  }

  private def tellAction[u: P]: P[TellAction] = {
    P(
      Keywords.tell ~/ location ~ messageConstructor ~ Readability.to.? ~/ messageTakingRef
    ).map { t => (TellAction.apply _).tupled(t) }
  }

  private def replyAction[u: P]: P[ReplyAction] = {
    P(
      Keywords.reply ~/ Readability.with_.? ~ location ~ messageConstructor
    ).map { t => (ReplyAction.apply _).tupled(t) }
  }

  private def askAction[u: P]: P[AskAction] = {
    P(
      Keywords.ask ~/ location ~ entityRef ~ Readability.to.? ~/ messageConstructor
    ).map { tpl => (AskAction.apply _).tupled(tpl) }
  }

  private def compoundAction[u: P]: P[CompoundAction] = {
    P(location ~ open ~ anyAction.rep(1, ",") ~ close)
      .map(tpl => (CompoundAction.apply _).tupled(tpl))
  }


  def anyAction[u: P]: P[Action] = {
    P(
      replyAction | setAction | appendAction | morphAction | becomeAction |
        yieldAction | returnAction | arbitraryAction | errorAction |
        publishAction | subscribeAction | tellAction | askAction | functionCallAction | compoundAction
    )
  }

}
