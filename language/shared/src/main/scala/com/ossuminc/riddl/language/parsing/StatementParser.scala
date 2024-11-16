/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** StatementParser Define actions that various constructs can take for modelling behavior in a message-passing system
  */
private[parsing] trait StatementParser {
  this: ReferenceParser & CommonParser =>

  private def arbitraryStatement[u: P]: P[ArbitraryStatement] = {
    P(Index ~ literalString ~/ Index)./ map { case (start, str, end) => ArbitraryStatement(at(start, end), str) }
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(
      Index ~ Keywords.error ~ literalString ~/ Index
    )./.map { case (start, str, end) => ErrorStatement(at(start, end), str) }
  }

  private def theSetStatement[u: P]: P[SetStatement] = {
    P(
      Index ~ Keywords.set ~/ fieldRef ~ to ~/ literalString ~/ Index
    )./.map { (start, ref, str, end) => SetStatement(at(start, end), ref, str) }
  }

  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      Index ~ Keywords.send ~/ messageRef ~/ to ~ (outletRef | inletRef) ~/ Index
    ).map { case (start, messageRef, portlet, end) => SendStatement(at(start, end), messageRef, portlet) }
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      Index ~ Keywords.tell ~/ messageRef ~/ to ~ processorRef ~/ Index
    )./.map { (start, msg, proc, end) => TellStatement(at(start, end), msg, proc) }
  }

  private def forEachStatement[u: P](set: StatementsSet): P[ForEachStatement] = {
    P(
      Index ~ Keywords.foreach ~/ (fieldRef | inletRef | outletRef) ~ Keywords.do_ ~/
        pseudoCodeBlock(set) ~ Keywords.end_ ~/ Index
    )./.map { case (start, ref, statements, end) =>
      val loc = at(start, end)
      ref match
        case fr: FieldRef  => ForEachStatement(loc, fr, statements.toContents)
        case ir: InletRef  => ForEachStatement(loc, ir, statements.toContents)
        case or: OutletRef => ForEachStatement(loc, or, statements.toContents)
        case r: Reference[?] =>
          error(loc, "Failed match case", "parsing a foreach statement") // shouldn't happen!
          ForEachStatement(loc, FieldRef(r.loc, r.pathId), statements.toContents)
    }
  }

  private def ifThenElseStatement[u: P](set: StatementsSet): P[IfThenElseStatement] = {
    P(
      Index ~ Keywords.`if` ~/ literalString ~ Keywords.`then` ~/ pseudoCodeBlock(set) ~ (
        Keywords.else_ ~ pseudoCodeBlock(set) ~/ Keywords.end_
      ).? ~ Index
    )./.map { case (start, cond, thens, maybeElses, end) =>
      val elses = maybeElses.getOrElse(Seq.empty[Statements])
      IfThenElseStatement(at(start, end), cond, thens.toContents, elses.toContents)
    }
  }

  private def callStatement[u: P]: P[CallStatement] = {
    P(Index ~ Keywords.call ~/ functionRef ~/ Index)./.map { case (start, ref, end) =>
      CallStatement(at(start, end), ref)
    }
  }

  private def stopStatement[u: P]: P[StopStatement] = {
    P(
      Index ~ Keywords.stop ~/ Index
    )./.map { case (start, end) => StopStatement(at(start, end)) }
  }

  enum StatementsSet:
    case AllStatements,
      AdaptorStatements,
      ApplicationStatements,
      ContextStatements,
      EntityStatements,
      FunctionStatements,
      ProjectorStatements,
      RepositoryStatements,
      SagaStatements,
      StreamStatements
  end StatementsSet

  private def morphStatement[u: P]: P[MorphStatement] = {
    P(
      Index ~ Keywords.morph ~/ entityRef ~/ to ~ stateRef ~/ `with` ~ messageRef ~/ Index
    )./.map { case (start, eRef, sRef, mRef, end) => MorphStatement(at(start, end), eRef, sRef, mRef) }
  }

  private def becomeStatement[u: P]: P[BecomeStatement] = {
    P(
      Index ~ Keywords.become ~/ entityRef ~ to ~ handlerRef ~/ Index
    )./.map { case (start, eRef, hRef, end) => BecomeStatement(at(start, end), eRef, hRef) }
  }

  private def focusStatement[u: P]: P[FocusStatement] = {
    P(Index ~ Keywords.focus ~/ Keywords.on ~ groupRef ~~ Index).map { case (start, ref, end) =>
      FocusStatement(at(start, end), ref)
    }
  }

  private def replyStatement[u: P]: P[ReplyStatement] = {
    P(Index ~ Keywords.reply ~/ `with`.?./ ~ messageRef ~~ Index).map { case (start, ref, end) =>
      ReplyStatement(at(start, end), ref)
    }
  }

  private def returnStatement[u: P]: P[ReturnStatement] = {
    P(
      Index ~ Keywords.`return` ~ literalString ~~ Index
    ).map { case (start, str, end) => ReturnStatement(at(start, end), str) }
  }

  private def readStatement[u: P]: P[ReadStatement] = {
    P(
      Index ~ StringIn("read", "get", "query", "find", "select").! ~ literalString ~
        from ~ typeRef ~ Keywords.where ~ literalString ~~ Index
    ).map { case (start, keyword, what, from, where, end) =>
      ReadStatement(at(start, end), keyword, what, from, where)
    }
  }

  private def writeStatement[u: P]: P[WriteStatement] = {
    P(
      Index ~ StringIn("write", "put", "create", "update", "delete", "remove", "append", "insert", "modify").! ~
        literalString ~ to ~ typeRef ~ Index
    ).map { case (start, keyword, what, to, end) =>
      WriteStatement(at(start, end), keyword, what, to)
    }
  }

  private def backTickElipsis[u: P]: P[Unit] = { P("```") }

  private def codeStatement[u: P]: P[CodeStatement] = {
    P(
      Index ~ backTickElipsis ~ Index ~ StringIn("scala", "java", "python", "mojo").! ~ Index ~
        until3('`', '`', '`') ~ Index
    ).map { case (at1, at2, lang, at3, contents, at4) =>
      CodeStatement(at(at1, at4), LiteralString(at(at2, at3), lang), contents)
    }
  }

  private def anyDefStatements[u: P](set: StatementsSet): P[Statements] = {
    P(
      sendStatement | arbitraryStatement | errorStatement | theSetStatement | tellStatement | callStatement |
        stopStatement | ifThenElseStatement(set) | forEachStatement(set) | codeStatement | comment
    ).asInstanceOf[P[Statements]]
  }

  def statement[u: P](set: StatementsSet): P[Statements] = {
    set match {
      case StatementsSet.AdaptorStatements     => anyDefStatements(set) | replyStatement
      case StatementsSet.ApplicationStatements => anyDefStatements(set) | focusStatement
      case StatementsSet.ContextStatements     => anyDefStatements(set) | replyStatement
      case StatementsSet.EntityStatements =>
        anyDefStatements(set) | morphStatement | becomeStatement | replyStatement
      case StatementsSet.FunctionStatements  => anyDefStatements(set) | returnStatement
      case StatementsSet.ProjectorStatements => anyDefStatements(set)
      case StatementsSet.RepositoryStatements =>
        anyDefStatements(set) | replyStatement | readStatement | writeStatement
      case StatementsSet.SagaStatements   => anyDefStatements(set) | returnStatement
      case StatementsSet.StreamStatements => anyDefStatements(set)
    }
  }

  def setOfStatements[u: P](set: StatementsSet): P[Seq[Statements]] = {
    P(statement(set).rep(0))./
  }

  def pseudoCodeBlock[u: P](set: StatementsSet): P[Seq[Statements]] = {
    P(
      undefined(Seq.empty[Statements]) |
        (open ~ undefined(Seq.empty[Statements]) ~ close) |
        (statement(set) | comment)./.rep(1) |
        (open ~ (statement(set) | comment)./.rep(1) ~ close)
    )
  }
}
