/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals
import com.reactific.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** ActionParser Define actions that various constructs can take for modelling
  * behavior in a message-passing system
  */
trait ActionParser extends ReferenceParser with ExpressionParser {

  def arbitraryAction[u: P]: P[ArbitraryAction] = {
    P(location ~ literalString ~ description).map { tpl =>
      (ArbitraryAction.apply _).tupled(tpl)
    }
  }

  def setAction[u: P]: P[SetAction] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Readability.to ~ expression ~
        description
    ).map { t => (SetAction.apply _).tupled(t) }
  }

  def morphAction[u: P]: P[MorphAction] = {
    P(
      Keywords.morph ~/ location ~ entityRef ~ Readability.to.? ~ stateRef ~
        description
    ).map { tpl => (MorphAction.apply _).tupled(tpl) }
  }

  def becomeAction[u: P]: P[BecomeAction] = {
    P(
      Keywords.become ~/ location ~ entityRef ~ Readability.to ~ handlerRef ~
        description
    ).map { tpl => (BecomeAction.apply _).tupled(tpl) }
  }

  def messageConstructor[u: P]: P[MessageConstructor] = {
    P(messageRef ~ argList).map(tpl => (MessageConstructor.apply _).tupled(tpl))
  }

  def publishAction[u: P]: P[PublishAction] = {
    P(
      Keywords.publish ~/ location ~ messageConstructor ~
        Terminals.Readability.to ~ pipeRef ~ description
    ).map { t => (PublishAction.apply _).tupled(t) }
  }

  def functionCallAction[u: P]: P[FunctionCallAction] = {
    P(Keywords.call ~/ location ~ pathIdentifier ~ argList ~ description)
      .map(tpl => (FunctionCallAction.apply _).tupled(tpl))
  }

  def tellAction[u: P]: P[TellAction] = {
    P(
      Keywords.tell ~/ location ~ messageConstructor ~ Readability.to.? ~/
        entityRef ~ description
    ).map { t => (TellAction.apply _).tupled(t) }
  }

  def replyAction[u: P]: P[ReplyAction] = {
    P(
      Keywords.reply ~/ Readability.with_.? ~ location ~ messageConstructor ~
        description
    ).map { t => (ReplyAction.apply _).tupled(t) }
  }

  def askAction[u: P]: P[AskAction] = {
    P(
      Keywords.ask ~/ location ~ entityRef ~ Readability.to.? ~/
        messageConstructor ~ description
    ).map { tpl => (AskAction.apply _).tupled(tpl) }
  }

  def compoundAction[u: P]: P[CompoundAction] = {
    P(location ~ open ~ anyAction.rep(1, ",") ~ close ~ description)
      .map(tpl => (CompoundAction.apply _).tupled(tpl))
  }

  def sagaStepAction[u: P]: P[SagaStepAction] = {
    P(
      arbitraryAction | publishAction | tellAction | askAction | replyAction |
        functionCallAction
    )
  }

  def anyAction[u: P]: P[Action] = {
    P(
      sagaStepAction | replyAction | setAction | morphAction | becomeAction |
        compoundAction
    )
  }

  def actionList[u: P]: P[Seq[Action]] = anyAction.rep(1)
}
