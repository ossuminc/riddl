/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** StatementParser
  *
  * Parse the declarative statements per riddlsim specification:
  * send, tell, morph, become, when, match, error, let, set, prompt, code
  */
private[parsing] trait StatementParser {
  this: ReferenceParser & CommonParser =>

  private def promptStatement[u: P]: P[PromptStatement] = {
    P(Index ~ Keywords.prompt ~ literalString ~/ Index)./ map { case (start, str, end) => PromptStatement(at(start, end), str) }
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

  enum StatementsSet:
    case AllStatements,
      AdaptorStatements,
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

  private def whenStatement[u: P](set: StatementsSet): P[WhenStatement] = {
    P(
      Index ~ Keywords.when ~/ literalString ~ Keywords.`then` ~/ pseudoCodeBlock(set) ~/ Keywords.end_ ~/ Index
    )./.map { case (start, cond, statements, end) =>
      WhenStatement(at(start, end), cond, statements.toContents)
    }
  }

  private def matchCase[u: P](set: StatementsSet): P[MatchCase] = {
    P(
      Index ~ Keywords.case_ ~/ literalString ~ open ~/ setOfStatements(set) ~ close ~/ Index
    )./.map { case (start, pattern, statements, end) =>
      MatchCase(at(start, end), pattern, statements.toContents)
    }
  }

  private def matchStatement[u: P](set: StatementsSet): P[MatchStatement] = {
    P(
      Index ~ Keywords.`match` ~/ literalString ~ open ~/
        matchCase(set).rep(1) ~
        (Keywords.default ~ open ~/ setOfStatements(set) ~ close).? ~/
        close ~/ Index
    )./.map { case (start, expr, cases, maybeDefault, end) =>
      val default = maybeDefault.getOrElse(Seq.empty[Statements])
      MatchStatement(at(start, end), expr, cases.toSeq, default.toContents)
    }
  }

  private def letStatement[u: P]: P[LetStatement] = {
    P(
      Index ~ Keywords.let ~/ identifier ~ Punctuation.equalsSign ~/ literalString ~/ Index
    )./.map { case (start, id, expr, end) =>
      LetStatement(at(start, end), id, expr)
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
      // GROUP 1: Control flow statements
      whenStatement(set) | matchStatement(set) |
      // GROUP 2: Common message operations
      sendStatement | tellStatement |
      // GROUP 3: Variable operations
      theSetStatement | letStatement |
      // GROUP 4: General statements
      promptStatement | codeStatement |
      // GROUP 5: Error handling
      errorStatement | comment
    ).asInstanceOf[P[Statements]]
  }

  def statement[u: P](set: StatementsSet): P[Statements] = {
    set match {
      case StatementsSet.AdaptorStatements     => anyDefStatements(set)
      case StatementsSet.ContextStatements     => anyDefStatements(set)
      case StatementsSet.EntityStatements      => anyDefStatements(set) | morphStatement | becomeStatement
      case StatementsSet.FunctionStatements    => anyDefStatements(set)
      case StatementsSet.ProjectorStatements   => anyDefStatements(set)
      case StatementsSet.RepositoryStatements  => anyDefStatements(set)
      case StatementsSet.SagaStatements        => anyDefStatements(set)
      case StatementsSet.StreamStatements      => anyDefStatements(set)
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
