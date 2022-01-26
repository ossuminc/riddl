package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** ActionParser
 * Define actions that various constructs can take for modelling behavior
 * in a message-passing system
 */
trait ActionParser extends ReferenceParser with ConditionParser with ExpressionParser {

  def arbitraryAction[u: P]: P[ArbitraryAction] = {
    P(
      location ~ literalString ~ description
    ).map { tpl => (ArbitraryAction.apply _).tupled(tpl) }
  }

  def setAction[u: P]: P[SetAction] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Readability.to ~ expression ~
        description
    ).map { t => (SetAction.apply _).tupled(t) }
  }

  def morphAction[u: P]: P[MorphAction] = {
    P(
      Keywords.morph ~/ location ~ entityRef ~ Readability.to.? ~ stateRef ~ description
    ).map { tpl => (MorphAction.apply _).tupled(tpl) }
  }

  def becomeAction[u: P]: P[BecomeAction] = {
    P(
      Keywords.become ~/ location ~ entityRef ~ Readability.to ~ handlerRef ~ description
    ).map { tpl => (BecomeAction.apply _).tupled(tpl) }
  }

  def messageConstructor[u: P]: P[MessageConstructor] = {
    P(messageRef ~ argList).map(tpl => (MessageConstructor.apply _).tupled(tpl))
  }

  def publishAction[u: P]: P[PublishAction] = {
    P(
      Keywords.publish ~/ location ~ messageConstructor ~ Terminals.Readability.to ~ pipeRef ~
        description
    ).map { t => (PublishAction.apply _).tupled(t) }
  }

  def tellAction[u: P]: P[TellAction] = {
    P(Keywords.tell ~/ location ~ entityRef ~ Readability.to.? ~/ messageConstructor ~ description)
      .map { t => (TellAction.apply _).tupled(t) }
  }

  def askAction[u: P]: P[AskAction] = {
    P(Keywords.ask ~/ location ~ entityRef ~ Readability.to.? ~/ messageConstructor ~ description)
      .map { tpl => (AskAction.apply _).tupled(tpl) }
  }

  def anyAction[u: P]: P[Action] = {
    P(
      arbitraryAction | setAction | morphAction | becomeAction | publishAction |
        tellAction | askAction
    )
  }
}
