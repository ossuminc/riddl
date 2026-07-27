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

  // `do "…"` is the canonical AI-action statement (A54). It builds a PromptStatement.
  private def doStatement[u: P]: P[PromptStatement] = {
    P(Index ~ Keywords.do_ ~ literalString ~/ Index)./ map { case (start, str, end) =>
      PromptStatement(at(start, end), str)
    }
  }

  // `prompt "…"` is the DEPRECATED synonym for `do "…"`; it still builds a PromptStatement but emits a
  // deprecation at the keyword (pattern mirrors replyStatement's `reply` -> `yield`). Note: the
  // parenthesized `prompt("…")` value form is handled by `promptValue`, not here.
  private def promptStatement[u: P]: P[PromptStatement] = {
    P(Index ~ Keywords.prompt ~ literalString ~/ Index)./ map { case (start, str, end) =>
      val kwLoc = at(start, start + Keyword.prompt.length)
      deprecation(kwLoc, "The `prompt` statement is deprecated; use `do` instead")
      PromptStatement(at(start, end), str)
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

  // A54: a message operand — a bare message ref `E` or a constructor `E(args)`. The ref is parsed
  // ONCE, then an OPTIONAL parenthesized arg list decides ref-vs-constructor. (Trying `constructor`
  // first would commit the ref parse via its internal cut and prevent the bare-ref fallback.)
  private def messageValue[u: P]: P[MessageRef | Constructor] = {
    P(
      Index ~ messageRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~ Index
    ).map {
      case (_, ref, None, _)             => ref: MessageRef | Constructor
      case (start, ref, Some(args), end) => Constructor(at(start, end), ref, args.toSeq)
    }
  }

  // A54: a record operand for `morph … with` — a bare record ref `R` or a constructor `R(args)`.
  private def recordValue[u: P]: P[RecordRef | Constructor] = {
    P(
      Index ~ recordRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~ Index
    ).map {
      case (_, ref, None, _)             => ref: RecordRef | Constructor
      case (start, ref, Some(args), end) => Constructor(at(start, end), ref, args.toSeq)
    }
  }

  private def yieldStatement[u: P]: P[YieldStatement] = {
    P(
      Index ~ Keywords.`yield` ~/ messageValue ~/ Index
    )./.map { case (start, msg, end) => YieldStatement(at(start, end), msg) }
  }

  // `reply` is the deprecated synonym for `yield`; it parses to the SAME YieldStatement and emits a
  // deprecation at the keyword (pattern mirrors StreamingParser's deprecated shape keywords).
  private def replyStatement[u: P]: P[YieldStatement] = {
    P(
      Index ~ Keywords.reply ~/ messageValue ~/ Index
    )./.map { case (start, msg, end) =>
      val kwLoc = at(start, start + Keyword.reply.length)
      deprecation(kwLoc, "The `reply` statement is deprecated; use `yield` instead")
      YieldStatement(at(start, end), msg)
    }
  }

  private def theSetStatement[u: P]: P[SetStatement] = {
    P(
      Index ~ Keywords.set ~/ (fieldRef | stateRef) ~ to ~/ value ~/ Index
    )./.map {
      case (start, ref: FieldRef, v, end) => SetStatement(at(start, end), ref, v)
      case (start, ref: StateRef, v, end) => SetStatement(at(start, end), ref, v)
    }
  }

  // `send` canonically targets an OUTLET: a processor emits on its own outlet and a Connector
  // routes the message to a downstream inlet. Sending directly to an INLET bypasses that model
  // (that is `tell`'s job), so the inlet form is DEPRECATED (soft, removed in 3.0). Both forms
  // still parse; the inlet branch emits a deprecation at the ref (mirrors reply -> yield, prompt).
  private def sendStatement[u: P]: P[SendStatement] = {
    P(
      Index ~ Keywords.send ~/ messageValue ~/ to ~ (outletRef | inletRef) ~/ Index
    )./.map { case (start, msg, portlet, end) =>
      portlet match
        case ref: InletRef =>
          deprecation(
            ref.loc,
            "send to an inlet is deprecated and will be removed in 3.0; send to your outlet and " +
              "connect it with a connector, or use `tell` to deliver directly to a processor"
          )
        case _ => ()
      SendStatement(at(start, end), msg, portlet)
    }
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      Index ~ Keywords.tell ~/ messageValue ~/ to ~ processorRef ~/ Index
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
      Index ~ Keywords.morph ~/ entityRef ~/ to ~ stateRef ~/ `with` ~ recordValue ~/ Index
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

  // A25: `foreach <element> in <collection> { <statements> }`. The `field` keyword disambiguates
  // the collection at parse time: `field X.Y` parses as a FieldRef (a collection-typed field);
  // a bare name parses as an Identifier (a `let`-bound local). Body statements are threaded with
  // the same `StatementsSet` so per-context restrictions apply inside the loop (mirror whenStatement).
  private def foreachCollection[u: P]: P[FieldRef | Identifier] = {
    // fastparse `|` unifies to the least upper bound (RiddlValue), so widen each branch to the
    // target union explicitly to keep the collection typed as `FieldRef | Identifier`.
    P(
      fieldRef.map(fr => fr: FieldRef | Identifier) |
        identifier.map(id => id: FieldRef | Identifier)
    )
  }

  private def foreachStatement[u: P](set: StatementsSet): P[ForeachStatement] = {
    P(
      Index ~ Keywords.foreach ~/ identifier ~ in ~ foreachCollection ~
        open ~/ setOfStatements(set) ~ close ~/ Index
    )./.map { case (start, element, collection, statements, end) =>
      ForeachStatement(at(start, end), element, collection, statements.toContents)
    }
  }

  private def letStatement[u: P]: P[LetStatement] = {
    P(
      Index ~ Keywords.let ~/ identifier ~ (Punctuation.colon ~ typeRef).? ~
        Punctuation.equalsSign ~/ value ~/ Index
    )./.map { case (start, id, optTypeRef, expr, end) =>
      LetStatement(at(start, end), id, optTypeRef, expr)
    }
  }

  // A54: `prompt("…")` — an AI-computed value. The parens distinguish it from the deprecated `prompt`
  // STATEMENT (`prompt "…"`, no parens). Tried before the bare-path `valueRef` in `value`.
  private def promptValue[u: P]: P[PromptValue] = {
    P(
      Index ~ Keywords.prompt ~ Punctuation.roundOpen ~/ literalString ~ Punctuation.roundClose ~/ Index
    )./.map { case (start, str, end) => PromptValue(at(start, end), str) }
  }

  // A54/A28: a value expression. Keyword-led forms (`prompt(…)`, constructor, `get from`) are tried
  // first; everything else flows through the boolean-expression sub-language (`booleanExpr`), which
  // returns the bare atom unchanged when no comparison/logical operator is present — so a plain
  // `let x = y` still yields exactly a `ValueRef`, not a wrapper. fastparse `|` unifies to the least
  // upper bound (RiddlValue), so each branch is widened to `Value` explicitly (mirror foreachCollection).
  def value[u: P]: P[Value] = {
    P(
      literalString.map(ls => ls: Value) |
        promptValue.map(pv => pv: Value) |
        constructor.map(c => c: Value) |
        getValue.map(gv => gv: Value) |
        booleanExpr
    )
  }

  // A28: the boolean-expression sub-language — a layered left-fold, loosest to tightest:
  //   or < and < not < comparison < atom.
  // CONTEXT-SENSITIVE OPERATORS: `and`/`or`/`not`/`true`/`false` are matched ONLY here (each with a
  // keyword word-boundary via `Keywords.keyword`, so `andrew`/`notify`/`truthy` stay identifiers).
  // They are NOT added to any global reserved-word filter, so they remain legal identifiers elsewhere.
  // Every level returns the bare `Value` atom when no operator is present, so plain values are
  // never wrapped.

  // `or` level (loosest). Left-associative fold of `and`-expressions.
  private def booleanExpr[u: P]: P[Value] = {
    P(Index ~ andExpr ~ (Keywords.keyword("or") ~/ andExpr).rep ~ Index).map {
      case (start, first, rest, end) =>
        rest.foldLeft(first)((l, r) => LogicalExpression(at(start, end), LogicalOperator.Or, l, r))
    }
  }

  // `and` level. Left-associative fold of `not`-expressions.
  private def andExpr[u: P]: P[Value] = {
    P(Index ~ notExpr ~ (Keywords.keyword("and") ~/ notExpr).rep ~ Index).map {
      case (start, first, rest, end) =>
        rest.foldLeft(first)((l, r) => LogicalExpression(at(start, end), LogicalOperator.And, l, r))
    }
  }

  // `not` level (prefix). Recurses so `not not a` works; falls through to `comparison`.
  private def notExpr[u: P]: P[Value] = {
    P(
      (Index ~ Keywords.keyword("not") ~/ notExpr ~ Index).map { case (start, inner, end) =>
        NotExpression(at(start, end), inner): Value
      } | comparison
    )
  }

  // comparison level (non-associative). One optional relational operator between two atoms; when
  // absent, the bare atom is returned unchanged (NOT wrapped in a BooleanExpression).
  private def comparison[u: P]: P[Value] = {
    P(Index ~ booleanAtom ~ (comparisonOperator ~/ booleanAtom).? ~ Index).map {
      case (start, left, Some((op, right)), end) =>
        ComparisonExpression(at(start, end), op, left, right)
      case (_, left, None, _) => left
    }
  }

  // Relational operators. `StringIn` is longest-match, so `<=`/`>=` win over `<`/`>` and `!=`/`==`
  // are matched as whole tokens.
  private def comparisonOperator[u: P]: P[ComparisonOperator] = {
    P(StringIn("==", "!=", "<=", ">=", "<", ">").!).map {
      case "==" => ComparisonOperator.EQ
      case "!=" => ComparisonOperator.NE
      case "<=" => ComparisonOperator.LE
      case ">=" => ComparisonOperator.GE
      case "<"  => ComparisonOperator.LT
      case _    => ComparisonOperator.GT
    }
  }

  // A28: a boolean literal (`true`/`false`), matched with a keyword word-boundary so `truthy` is not
  // read as `true` + `thy`.
  private def booleanLiteral[u: P]: P[BooleanLiteral] = {
    P(
      Index ~ (Keywords.keyword("true").map(_ => true) | Keywords
        .keyword("false")
        .map(_ => false)) ~ Index
    ).map { case (start, b, end) => BooleanLiteral(at(start, end), b) }
  }

  // A28: an atom reachable through a boolean expression: a boolean literal, a parenthesized boolean
  // expression, or any existing value atom (so a comparison operand can be a literal/get/constructor/
  // prompt/ref). `booleanLiteral` precedes `valueRef` so `true`/`false` are literals here; `valueRef`
  // stays last (permissive bare path).
  private def booleanAtom[u: P]: P[Value] = {
    P(
      booleanLiteral.map(bl => bl: Value) |
        (Punctuation.roundOpen ~ booleanExpr ~ Punctuation.roundClose) |
        literalString.map(ls => ls: Value) |
        promptValue.map(pv => pv: Value) |
        constructor.map(c => c: Value) |
        getValue.map(gv => gv: Value) |
        valueRef.map(vr => vr: Value)
    )
  }

  // A54: `(command|event|query|result|record <path>)(<args>)`. Positional args (a bare `value`) or
  // named args (`id = value`); ordering (positional before named) is enforced at validation time.
  private def constructorArg[u: P]: P[ConstructorArg] = {
    P(
      Index ~ (
        (identifier ~ Punctuation.equalsSign ~/ value).map { case (id, v) =>
          (Some(id): Option[Identifier], v)
        } |
          value.map(v => (None: Option[Identifier], v))
      ) ~ Index
    ).map { case (start, (name, v), end) => ConstructorArg(at(start, end), name, v) }
  }

  private def constructor[u: P]: P[Constructor] = {
    P(
      Index ~ (messageRef.map(mr => mr: MessageRef | RecordRef) |
        recordRef.map(rr => rr: MessageRef | RecordRef)) ~
        Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
        Punctuation.roundClose ~/ Index
    )./.map { case (start, ref, args, end) =>
      Constructor(at(start, end), ref, args.toSeq)
    }
  }

  // A45/A45b: `get from (input <ref> | state <ref>)`. The ref parsers already consume their leading
  // keyword (`input`/aliases, `state`).
  private def getValue[u: P]: P[GetValue] = {
    P(
      Index ~ Keywords.get ~/ from ~/ (inputRef.map(ir => ir: InputRef | StateRef) |
        stateRef.map(sr => sr: InputRef | StateRef)) ~/ Index
    )./.map { case (start, source, end) => GetValue(at(start, end), source) }
  }

  // A54: a bare path identifier naming a value in scope. Resolved to a let-local, message field,
  // state field, or (in a return) a function input at validation time.
  private def valueRef[u: P]: P[ValueRef] = {
    P(Index ~ pathIdentifier ~ Index).map { case (start, pid, end) =>
      ValueRef(at(start, end), pid)
    }
  }

  // A45: `put <value> to output <ref>`. Scope-gated to Context (application) handlers by putStatements.
  private def putStatement[u: P]: P[PutStatement] = {
    P(
      Index ~ Keywords.put ~/ value ~ to ~/ outputRef ~/ Index
    )./.map { case (start, v, out, end) => PutStatement(at(start, end), v, out) }
  }

  // A57: `return <value>`. Scope-gated to Function bodies by returnStatements.
  private def returnStatement[u: P]: P[ReturnStatement] = {
    P(
      Index ~ Keywords.`return` ~/ value ~/ Index
    )./.map { case (start, v, end) => ReturnStatement(at(start, end), v) }
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

  // A45: `put ... to output ...` is allowed only in a Context (application) handler; banned
  // elsewhere at the keyword with a clear message (inverse of A26's function bans).
  private def putStatements[u: P](set: StatementsSet): P[Statements] =
    if set.processor == ProcessorKind.Context then putStatement.asInstanceOf[P[Statements]]
    else
      (P(Keywords.put) ~/ Fail.opaque(
        "'put' is only allowed in an application (context) handler; it publishes a value to a UI " +
          "output which only exists in an application context"
      )).asInstanceOf[P[Statements]]

  // A57: `return ...` is allowed only in a Function body; banned elsewhere at the keyword.
  private def returnStatements[u: P](set: StatementsSet): P[Statements] =
    if set.processor == ProcessorKind.Function then returnStatement.asInstanceOf[P[Statements]]
    else
      (P(Keywords.`return`) ~/ Fail.opaque(
        "'return' is only allowed in a function body; it returns the function's result value"
      )).asInstanceOf[P[Statements]]

  private def anyDefStatements[u: P](set: StatementsSet): P[Statements] = {
    P(
      // GROUP 1: Control flow statements
      whenStatement(set) | matchStatement(set) | foreachStatement(set) |
        // GROUP 2: Common message operations (suppressed under ActivationClause)
        messagingStatements(set) |
        // GROUP 3: Variable operations (set is banned in a pure Function body — see setStatements)
        setStatements(set) | letStatement |
        // GROUP 3b: Boundary value operations, scope-gated (A45 put -> Context; A57 return -> Function)
        putStatements(set) | returnStatements(set) |
        // GROUP 4: General statements (`do` is canonical; `prompt` is a deprecated synonym)
        doStatement | promptStatement | codeStatement |
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
