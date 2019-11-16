package com.yoppworks.ossum.riddl.language

import AST._
import Terminals._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ConsumerParser */
trait ConsumerParser
    extends CommonParser
    with ConditionParser
    with ExpressionParser {

  def setStmt[_: P](): P[SetStatement] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~
        expression ~ description
    ).map { t =>
      (SetStatement.apply _).tupled(t)
    }
  }

  def appendStmt[_: P]: P[AppendStatement] = {
    P(
      Keywords.append ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~
        identifier ~ description
    ).map { t =>
      (AppendStatement.apply _).tupled(t)
    }
  }

  def publishStmt[_: P]: P[PublishStatement] = {
    P(
      Keywords.publish ~/ location ~ messageRef ~ Terminals.Readability.to ~
        topicRef ~ description
    ).map { t =>
      (PublishStatement.apply _).tupled(t)
    }
  }

  def messageConstructor[_: P]: P[MessageConstructor] = {
    P(messageRef ~ argList).map(tpl => (MessageConstructor.apply _).tupled(tpl))
  }

  def sendStmt[_: P]: P[SendStatement] = {
    P(
      Keywords.send ~/ location ~ messageConstructor ~
        Terminals.Readability.to ~
        (entityRef | topicRef) ~ description
    ).map { t =>
      (SendStatement.apply _).tupled(t)
    }
  }

  def removeStmt[_: P](): P[RemoveStatement] = {
    P(
      Keywords.remove ~/ location ~ pathIdentifier ~ Readability.from ~
        pathIdentifier ~ description
    ).map { t =>
      (RemoveStatement.apply _).tupled(t)
    }
  }

  def doStmt[_: P]: P[ExecuteStatement] = {
    P(location ~ Keywords.execute ~/ identifier ~ description).map { t =>
      (ExecuteStatement.apply _).tupled(t)
    }
  }

  def whenStmt[_: P]: P[WhenStatement] = {
    P(
      location ~ Keywords.when ~/ condition ~ Keywords.then_.? ~
        Punctuation.curlyOpen ~/ onClauseAction.rep ~ Punctuation.curlyClose ~
        description
    ).map(t => (WhenStatement.apply _).tupled(t))
  }

  def onClauseAction[_: P]: P[OnClauseStatement] = {
    P(
      setStmt | appendStmt | removeStmt | sendStmt | publishStmt | whenStmt |
        doStmt
    )
  }

  def onClause[_: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~ onClauseAction.rep ~
      close ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def consumer[_: P]: P[Consumer] = {
    P(
      Keywords.consumer ~/ location ~ identifier ~
        (Readability.of | Readability.for_ | Readability.from) ~ topicRef ~ is ~
        ((open ~ undefined ~ close).map(_ => Seq.empty[OnClause]) |
          optionalNestedContent(onClause)) ~ description
    ).map(t => (Consumer.apply _).tupled(t))
  }
}
