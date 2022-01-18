package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import com.yoppworks.ossum.riddl.language.Terminals.Readability
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.collection.immutable.ListMap

/** Parser for an entity handler definition */
trait HandlerParser extends ReferenceParser with ConditionParser with ExpressionParser {

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
    P(messageRef ~ argList.?).map(tpl => {
      val args = tpl._2 match {
        case None    => ListMap.empty[Identifier, Expression]
        case Some(a) => a
      }
      MessageConstructor(tpl._1, args)
    })
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
        onClauseAction.rep ~ Punctuation.curlyClose ~ description
    ).map(t => (WhenStatement.apply _).tupled(t))
  }

  def onClauseAction[u: P]: P[OnClauseStatement] = {
    P(assignStmt | appendStmt | removalStmt | sendStmt | publishStmt | whenStmt | executeStmt)
  }

  def onClause[u: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~ onClauseAction.rep ~ close ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def handler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~
        ((open ~ undefined(Seq.empty[OnClause]) ~ close) | optionalNestedContent(onClause)) ~
        description
    ).map(t => (Handler.apply _).tupled(t))
  }
}
