package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Punctuation, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** ActionParser
 * Define actions that various constructs can take for modelling behavior
 * in a message-passing system
 */
trait ActionParser extends ReferenceParser with ConditionParser with ExpressionParser {

  def assignStmt[u: P]: P[SetStatement] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~ expression ~
        description
    ).map { t => (SetStatement.apply _).tupled(t) }
  }

  def appendStmt[u: P]: P[AppendStatement] = {
    P(
      Keywords.append ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~ identifier ~
        description
    ).map { t => (AppendStatement.apply _).tupled(t) }
  }

  def messageConstructor[u: P]: P[MessageConstructor] = {
    P(messageRef ~ argList).map(tpl => (MessageConstructor.apply _).tupled(tpl))
  }

  def publishStmt[u: P]: P[PublishStatement] = {
    P(
      Keywords.publish ~/ location ~ messageConstructor ~ Terminals.Readability.to ~ pipeRef ~
        description
    ).map { t => (PublishStatement.apply _).tupled(t) }
  }

  def sendStmt[u: P]: P[SendStatement] = {
    P(Keywords.send ~/ location ~ messageConstructor ~ Readability.to ~/ entityRef ~ description)
      .map { t => (SendStatement.apply _).tupled(t) }
  }

  def removalStmt[u: P]: P[RemoveStatement] = {
    P(
      Keywords.remove ~/ location ~ pathIdentifier ~ Readability.from ~ pathIdentifier ~ description
    ).map { t => (RemoveStatement.apply _).tupled(t) }
  }

  def executeStmt[u: P]: P[ExecuteStatement] = {
    P(location ~ Keywords.execute ~/ identifier ~ description).map { t =>
      (ExecuteStatement.apply _).tupled(t)
    }
  }

  def whenStmt[u: P]: P[WhenStatement] = {
    P(
      location ~ Keywords.when ~/ condition ~ Keywords.then_.? ~ Punctuation.curlyOpen ~/
        anyAction.rep ~ Punctuation.curlyClose ~ description
    ).map(t => (WhenStatement.apply _).tupled(t))
  }

  def anyAction[u: P]: P[OnClauseStatement] = {
    P(assignStmt | appendStmt | removalStmt | sendStmt | publishStmt | whenStmt | executeStmt)
  }


}
