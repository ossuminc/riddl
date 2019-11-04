package com.yoppworks.ossum.riddl.language

import AST._
import Terminals._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ConsumerParser */
trait ConsumerParser extends CommonParser with ConditionParser {

  def setStmt[_: P](): P[SetStatement] = {
    P(
      Keywords.set ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~
        pathIdentifier ~ description
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

  def sendStmt[_: P]: P[SendStatement] = {
    P(
      Keywords.send ~/ location ~ messageRef ~ Terminals.Readability.to ~
        entityRef ~ description
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

  def whenStmt[_: P]: P[WhenStatement] = {
    P(
      location ~ Keywords.when ~/ condition ~ Keywords.then_ ~
        Punctuation.curlyOpen ~ onClauseAction.rep ~ Punctuation.curlyClose ~
        description
    ).map(t => (WhenStatement.apply _).tupled(t))
  }

  def onClauseAction[_: P]: P[OnClauseStatement] = {
    P(setStmt | appendStmt | removeStmt | sendStmt | publishStmt | whenStmt)
  }

  def onClause[_: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~ onClauseAction.rep ~ close
  }.map(t => (OnClause.apply _).tupled(t))

  def consumer[_: P]: P[Consumer] = {
    P(
      Keywords.consumer ~/ location ~ identifier ~
        (Readability.of | Readability.for_ | Readability.from) ~ topicRef ~ is ~
        optionalNestedContent(onClause) ~ description
    ).map(t => (Consumer.apply _).tupled(t))
  }
}
