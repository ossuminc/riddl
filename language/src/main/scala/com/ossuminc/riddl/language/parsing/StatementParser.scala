/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** StatementParser
  *
  * Parse the declarative statements per riddlsim specification: send, tell, morph, become, when,
  * match, error, let, set, prompt, code
  */
private[parsing] trait StatementParser {
  this: ReferenceParser & CommonParser =>

  private def promptStatement[u: P]: P[PromptStatement] = {
    P(Index ~ (Keywords.prompt | Keywords.do_) ~ literalString ~/ Index)./ map {
      case (start, str, end) => PromptStatement(at(start, end), str)
    }
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(
      Index ~ Keywords.error ~ literalString ~/ Index
    )./.map { case (start, str, end) => ErrorStatement(at(start, end), str) }
  }

  private def requireStatement[u: P]: P[RequireStatement] = {
    P(
      Index ~ Keywords.require ~/ (literalString | (Keywords.invariant ~ pathIdentifier).map {
        case pid => pid
      }) ~/ Index
    )./.map {
      case (start, str: LiteralString, end) => RequireStatement(at(start, end), str)
      case (start, pid: PathIdentifier, end) =>
        RequireStatement(at(start, end), InvariantRef(at(start, end), pid))
    }
  }

  private def yieldStatement[u: P]: P[YieldStatement] = {
    P(
      Index ~ Keywords.`yield` ~/ messageRef ~/ Index
    )./.map { case (start, msg, end) => YieldStatement(at(start, end), msg) }
  }

  // `reply` is the deprecated synonym for `yield`; it parses to the SAME YieldStatement and emits a
  // deprecation at the keyword (pattern mirrors StreamingParser's deprecated shape keywords).
  private def replyStatement[u: P]: P[YieldStatement] = {
    P(
      Index ~ Keywords.reply ~/ messageRef ~/ Index
    )./.map { case (start, msg, end) =>
      val kwLoc = at(start, start + Keyword.reply.length)
      deprecation(kwLoc, "The `reply` statement is deprecated; use `yield` instead")
      YieldStatement(at(start, end), msg)
    }
  }

  private def theSetStatement[u: P]: P[SetStatement] = {
    P(
      Index ~ Keywords.set ~/ (fieldRef | stateRef) ~ to ~/ literalString ~/ Index
    )./.map {
      case (start, ref: FieldRef, str, end) => SetStatement(at(start, end), ref, str)
      case (start, ref: StateRef, str, end) => SetStatement(at(start, end), ref, str)
    }
  }

  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      Index ~ Keywords.send ~/ messageRef ~/ to ~ (outletRef | inletRef) ~/ Index
    ).map { case (start, messageRef, portlet, end) =>
      SendStatement(at(start, end), messageRef, portlet)
    }
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      Index ~ Keywords.tell ~/ messageRef ~/ to ~ processorRef ~/ Index
    )./.map { (start, msg, proc, end) => TellStatement(at(start, end), msg, proc) }
  }

  /** The processor context a statement occurs in — drives which extra statements are added (Entity
    * adds morph/become/reply; Context/Repository add reply).
    */
  enum ProcessorKind:
    case Any, Adaptor, Context, Entity, Function, Projector, Repository, Saga, Stream
  end ProcessorKind

  /** A per-clause restriction that composes with the processor context by *subtracting* statements.
    * Threads through nested blocks (when/match) via the same [[StatementsSet]].
    */
  enum ClauseRestriction:
    case Unrestricted
    case EventClause // events must always be accepted -> no require/error
    case ActivationClause // activate/passivate must be side-effect-free -> no send/tell/reply/morph/become
  end ClauseRestriction

  /** What statements are legal in a clause body: the processor context combined with an optional
    * per-clause restriction. Convenience vals on the companion preserve the old `StatementsSet.X`
    * call sites (now `Unrestricted`); `.forEvent`/`.forActivation` layer a restriction on.
    */
  case class StatementsSet(
    processor: ProcessorKind,
    clause: ClauseRestriction = ClauseRestriction.Unrestricted
  ):
    def forEvent: StatementsSet = copy(clause = ClauseRestriction.EventClause)
    def forActivation: StatementsSet = copy(clause = ClauseRestriction.ActivationClause)
  end StatementsSet

  object StatementsSet:
    val AllStatements: StatementsSet = StatementsSet(ProcessorKind.Any)
    val AdaptorStatements: StatementsSet = StatementsSet(ProcessorKind.Adaptor)
    val ContextStatements: StatementsSet = StatementsSet(ProcessorKind.Context)
    val EntityStatements: StatementsSet = StatementsSet(ProcessorKind.Entity)
    val FunctionStatements: StatementsSet = StatementsSet(ProcessorKind.Function)
    val ProjectorStatements: StatementsSet = StatementsSet(ProcessorKind.Projector)
    val RepositoryStatements: StatementsSet = StatementsSet(ProcessorKind.Repository)
    val SagaStatements: StatementsSet = StatementsSet(ProcessorKind.Saga)
    val StreamStatements: StatementsSet = StatementsSet(ProcessorKind.Stream)
  end StatementsSet

  private def morphStatement[u: P]: P[MorphStatement] = {
    P(
      Index ~ Keywords.morph ~/ entityRef ~/ to ~ stateRef ~/ `with` ~ recordRef ~/ Index
    )./.map { case (start, eRef, sRef, mRef, end) =>
      MorphStatement(at(start, end), eRef, sRef, mRef)
    }
  }

  private def becomeStatement[u: P]: P[BecomeStatement] = {
    P(
      Index ~ Keywords.become ~/ entityRef ~ to ~ handlerRef ~/ Index
    )./.map { case (start, eRef, hRef, end) => BecomeStatement(at(start, end), eRef, hRef) }
  }

  private def whenCondition[u: P]: P[(LiteralString | Identifier, Boolean)] = {
    P(
      literalString.map(ls => (ls, false)) |
        (Punctuation.exclamation ~ identifier).map(id => (id, true)) |
        identifier.map(id => (id, false))
    )
  }

  private def whenStatement[u: P](set: StatementsSet): P[WhenStatement] = {
    P(
      Index ~ Keywords.when ~/ whenCondition ~ Keywords.`then` ~/
        pseudoCodeBlock(set) ~/
        (Keywords.else_ ~/ pseudoCodeBlock(set)).? ~/
        Keywords.end_ ~/ Index
    )./.map { case (start, (cond, negated), thenStmts, elseStmtsOpt, end) =>
      val elseStmts = elseStmtsOpt.getOrElse(Seq.empty[Statements])
      WhenStatement(at(start, end), cond, thenStmts.toContents, elseStmts.toContents, negated)
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
      Index ~ Keywords.let ~/ identifier ~ (Punctuation.colon ~ typeRef).? ~
        Punctuation.equalsSign ~/ literalString ~/ Index
    )./.map { case (start, id, optTypeRef, expr, end) =>
      LetStatement(at(start, end), id, optTypeRef, expr)
    }
  }

  private def backTickEllipsis[u: P]: P[Unit] = { P("```") }

  private def codeStatement[u: P]: P[CodeStatement] = {
    P(
      Index ~ backTickEllipsis ~ Index ~ StringIn("scala", "java", "python", "mojo").! ~ Index ~
        until3('`', '`', '`') ~ Index
    ).map { case (at1, at2, lang, at3, contents, at4) =>
      CodeStatement(at(at1, at4), LiteralString(at(at2, at3), lang), contents)
    }
  }

  // Per-clause subtractions compose with the processor context (added in `statement`). These MUST
  // be `def`s (not vals) — a fastparse `P[T]` is a parsing run, not a reusable parser, so a val
  // would execute at its definition position and corrupt the alternation. A banned statement is
  // rejected by matching its keyword and cutting, so the error is reported at the offending keyword
  // with a clear message rather than as a downstream "expected }".
  private def messagingStatements[u: P](set: StatementsSet): P[Statements] =
    if set.clause == ClauseRestriction.ActivationClause then
      // Ban ALL outbound/identity messaging uniformly so each gives the same clear message
      // (send/tell live here; reply/morph/become are otherwise added by `statement` for entities).
      (P(
        Keywords.send | Keywords.tell | Keywords.`yield` | Keywords.reply | Keywords.morph |
          Keywords.become
      ) ~/ Fail.opaque(
        "'send'/'tell'/'yield'/'reply'/'morph'/'become' are not allowed in an " +
          "'on activate'/'on passivate' clause; activation and passivation must be side-effect-free"
      )).asInstanceOf[P[Statements]]
    else if set.processor == ProcessorKind.Function then
      // A26: a Function is pure — no outbound messaging. (reply/morph/become are already not offered
      // to a function by `statement`; set is banned in `setStatements`.)
      (P(Keywords.send | Keywords.tell) ~/ Fail.opaque(
        "'send'/'tell' are not allowed in a function body; a function is pure — messaging happens in " +
          "the calling on-clause based on the function's result"
      )).asInstanceOf[P[Statements]]
    else (sendStatement | tellStatement).asInstanceOf[P[Statements]]

  // A26: a Function is pure — it may not write entity state, so `set` is rejected in a function body.
  private def setStatements[u: P](set: StatementsSet): P[Statements] =
    if set.processor == ProcessorKind.Function then
      (P(Keywords.set) ~/ Fail.opaque(
        "'set' is not allowed in a function body; a function is pure — the on-clause effects state " +
          "based on the function's returned result"
      )).asInstanceOf[P[Statements]]
    else theSetStatement.asInstanceOf[P[Statements]]

  private def guardStatements[u: P](set: StatementsSet): P[Statements] =
    if set.clause == ClauseRestriction.EventClause then
      (P(Keywords.error | Keywords.require) ~/ Fail.opaque(
        "'require'/'error' are not allowed in an 'on event' clause; events must always be accepted"
      )).asInstanceOf[P[Statements]]
    else (errorStatement | requireStatement).asInstanceOf[P[Statements]]

  private def anyDefStatements[u: P](set: StatementsSet): P[Statements] = {
    P(
      // GROUP 1: Control flow statements
      whenStatement(set) | matchStatement(set) |
        // GROUP 2: Common message operations (suppressed under ActivationClause)
        messagingStatements(set) |
        // GROUP 3: Variable operations (set is banned in a pure Function body — see setStatements)
        setStatements(set) | letStatement |
        // GROUP 4: General statements
        promptStatement | codeStatement |
        // GROUP 5: Error handling and preconditions (suppressed under EventClause)
        guardStatements(set) | comment
    ).asInstanceOf[P[Statements]]
  }

  def statement[u: P](set: StatementsSet): P[Statements] = {
    val base = anyDefStatements(set)
    // Under an ActivationClause the outbound/identity statements (reply/morph/become) are
    // suppressed too (send/tell are already suppressed in anyDefStatements) — activation and
    // passivation must be side-effect-free. Otherwise add the processor's extras as before.
    if set.clause == ClauseRestriction.ActivationClause then base
    else
      set.processor match {
        case ProcessorKind.Entity =>
          base | morphStatement | becomeStatement | yieldStatement | replyStatement
        // A26: a Function is pure. send/tell/set are banned inside `base`; morph/become/yield/reply
        // are caught here (appended after `base`, so valid statements match first) with a clear
        // message.
        case ProcessorKind.Function =>
          base | (P(
            Keywords.morph | Keywords.become | Keywords.`yield` | Keywords.reply
          ) ~/ Fail.opaque(
            "'morph'/'become'/'yield'/'reply' are not allowed in a function body; a function is " +
              "pure and may not change entity state or yield"
          )).asInstanceOf[P[Statements]]
        case ProcessorKind.Context    => base | yieldStatement | replyStatement
        case ProcessorKind.Repository => base | yieldStatement | replyStatement
        case _                        => base
      }
  }

  private def setOfStatements[u: P](set: StatementsSet): P[Seq[Statements]] = {
    P(statement(set).rep(0))./
  }

  def pseudoCodeBlock[u: P](set: StatementsSet): P[Seq[Statements]] = {
    P(
      undefined(Seq.empty[Statements]) |
        // Allow { ??? }, { // comment ??? }, { ??? // comment }, { // c1 ??? // c2 }
        (open ~ comment.rep(0) ~ undefined(Seq.empty[Statements]) ~ comment.rep(0) ~ close).map {
          case (before, _, after) => before ++ after
        } |
        (statement(set) | comment)./.rep(1) |
        (open ~ (statement(set) | comment)./.rep(1) ~ close)
    )
  }
}
