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
      deprecation(
        kwLoc,
        "The `prompt` statement is deprecated; use `do` instead",
        code = Option(Messages.DeprecationCode.PromptStatement),
        autoFixable = true
      )
      PromptStatement(at(start, end), str)
    }
  }

  private def errorStatement[u: P]: P[ErrorStatement] = {
    P(
      Index ~ Keywords.error ~ literalString ~/ Index
    )./.map { case (start, str, end) => ErrorStatement(at(start, end), str) }
  }

  // A28: `require` accepts a pseudo-code LiteralString, an `invariant <pathId>` reference, or a
  // structured BooleanExpression. `booleanExprOnly` is tried LAST: `require invariant X` is caught by
  // the `invariant`-keyword arm first, and a bare-ref pseudo-condition (never valid in `require`) is
  // rejected by the filter (so `require count == 0` becomes a BooleanExpression while the legacy
  // forms are unchanged).
  private def requireStatement[u: P]: P[RequireStatement] = {
    P(
      Index ~ Keywords.require ~/ (
        literalString |
          (Keywords.invariant ~ pathIdentifier).map { case pid => pid } |
          booleanExprOnly
      ) ~ (Keywords.`with` ~ value).? ~/ Index
    )./.map {
      case (start, str: LiteralString, arg, end) => RequireStatement(at(start, end), str, arg)
      case (start, pid: PathIdentifier, arg, end) =>
        RequireStatement(at(start, end), InvariantRef(at(start, end), pid), arg)
      case (start, be: BooleanExpression, arg, end) => RequireStatement(at(start, end), be, arg)
    }
  }

  // A28: an invariant's condition is either an opaque pseudo-code LiteralString or a structured
  // BooleanExpression (or absent via `undefined`/`???`). Lives here (rather than in CommonParser)
  // so it can reach `booleanExprOnly`; every caller (ProcessorParser/EntityParser/NebulaParser/
  // ExtensibleTopLevelParser) mixes in StatementParser transitively. ORDER: `literalString` MUST
  // precede `booleanExprOnly` — `literalString` cuts after the opening quote, so a quoted string fed
  // to `booleanExprOnly` first would fail the filter behind that cut (no backtrack). A quoted string
  // and an unquoted expression never share a first token, so trying `literalString` first is safe:
  // `invariant X is "…"` stays a LiteralString, `invariant X is a > b` becomes a BooleanExpression.
  /** The optional `requires` clause: a STATE ref or a TYPE ref, never an inline aggregation.
    *
    * `requires state S` narrows an entity-level invariant to one state and stays IMPLICIT.
    * `requires <type>` makes it explicit-only, since nothing in ambient scope can supply the value.
    * `stateRef` is tried first because `state` is a keyword `typeRef` would not claim.
    */
  private def invariantRequires[u: P]: P[StateRef | TypeRef] =
    P(Keywords.requires ~/ (stateRef | typeRef)).asInstanceOf[P[StateRef | TypeRef]]

  /** The block condition: pure statements then the boolean that IS the predicate.
    *
    * Reuses `StatementsSet.FunctionStatements` rather than defining a new set — A26 already makes
    * that set pure (send/tell/set banned in `base`; morph/become/yield/reply rejected with a
    * message), which is exactly the purity an invariant needs.
    */
  private def invariantBlock[u: P]: P[InvariantBlock] = {
    P(
      Index ~ open ~ statement(StatementsSet.FunctionStatements).rep(0) ~ booleanExprOnly ~ close ~
        Index
    ).map { case (start, stmts, predicate, end) =>
      InvariantBlock(at(start, end), stmts.toContents, predicate)
    }
  }

  def invariant[u: P]: P[Invariant] = {
    type Cond = Option[LiteralString | BooleanExpression | InvariantBlock]
    P(
      Index ~ Keywords.invariant ~ identifier ~/ invariantRequires.? ~ is ~ (
        undefined(Option.empty[LiteralString | BooleanExpression | InvariantBlock]) |
          // ORDER: the block form leads because it is the only arm starting with `{`, and
          // `booleanExprOnly` would otherwise try (and fail behind a cut) on the brace.
          invariantBlock.map(b => Some(b): Cond) |
          literalString.map(ls => Some(ls): Cond) |
          booleanExprOnly.map(be => Some(be): Cond)
      ) ~ withMetaData ~/ Index
    ).map { case (off1, id, requires, condition, metas, off2) =>
      Invariant(at(off1, off2), id, condition, requires, metas.toContents)
    }
  }

  /** A56: a bare path naming a binding introduced by the enclosing on-clause — `on p: command Ping
    * is { tell p to entity F }`.
    *
    * Tried only AFTER [[messageRef]], which is keyword-led (`command`/`event`/`query`/`result`) and
    * carries no cut at the keyword, so this alternative can never shadow `tell command Foo` — the
    * same backtracking bargain [[HandlerParser.maybeName]] relies on.
    *
    * The `!to` guard matters because `anyIdentifier` does NOT exclude keywords: without it, a
    * missing operand (`tell to entity F`) would consume `to` AS the operand and then report the
    * failure a token late, against the definition rather than the omission.
    */
  private def boundMessageValue[u: P]: P[ValueRef] = {
    P(Index ~ !to ~ pathIdentifier ~~ Index).map { case (start, pid, end) =>
      ValueRef(at(start, end), pid)
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

  /** A56: the operand accepted by `tell`, `send`, `yield` and `reply` — [[messageValue]] widened
    * with a bound name.
    *
    * `yield`/`reply` were held back from A56 on the reasoning that `yield p` "would interact with
    * yield conformance (A19), which compares the yielded operand against the clause's DECLARED
    * `yields`". **Task 2 of the message-value design overturns that.** The comparison is by
    * RESOLVED TYPE, and a `ValueRef` supplies one exactly as a `MessageRef` does — so conformance
    * is not an obstacle to widening, it is a check that has to keep working across it, which
    * `YieldReplyMorphValueOperandTest` pins from both directions.
    *
    * The ordering matters: `messageValue` is keyword-led, so it must be tried FIRST; a bare path is
    * only reached when no message kind keyword is present.
    */
  private def deliverableMessageValue[u: P]: P[MessageRef | Constructor | ValueRef] = {
    P(messageValue | boundMessageValue)
  }

  // A54: a record operand for `morph … with` — a bare record ref `R` or a constructor `R(args)`.
  // Task 2 widens it with a bare path naming a value already in hand, the record-side counterpart
  // of `deliverableMessageValue`. Same ordering rule: `recordRef` is keyword-led and goes first.
  private def recordValue[u: P]: P[RecordRef | Constructor | ValueRef] = {
    P(
      Index ~ recordRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~ Index
    ).map {
      case (_, ref, None, _)             => ref: RecordRef | Constructor | ValueRef
      case (start, ref, Some(args), end) => Constructor(at(start, end), ref, args.toSeq)
    } | boundMessageValue
  }

  private def yieldStatement[u: P]: P[YieldStatement] = {
    P(
      Index ~ Keywords.`yield` ~/ deliverableMessageValue ~/ Index
    )./.map { case (start, msg, end) => YieldStatement(at(start, end), msg) }
  }

  // `reply` was a DEPRECATED synonym for `yield` until 2.0, parsing to the same YieldStatement.
  // It is now un-deprecated and builds its own node: `reply` answers a QUERY with its declared
  // result, where `yield` emits an EVENT from a command. Two pairings, two spellings -- see
  // AST.ReplyStatement. The pairing itself (`reply result` / `yield event`) is enforced in
  // ValidationPass, which can name both the keyword and the message kind; a parse failure here
  // could only point at the keyword.
  private def replyStatement[u: P]: P[ReplyStatement] = {
    P(
      Index ~ Keywords.reply ~/ deliverableMessageValue ~/ Index
    )./.map { case (start, msg, end) => ReplyStatement(at(start, end), msg) }
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
      Index ~ Keywords.send ~/ deliverableMessageValue ~/ to ~ (outletRef | inletRef) ~/ Index
    )./.map { case (start, msg, portlet, end) =>
      portlet match
        case ref: InletRef =>
          deprecation(
            ref.loc,
            "send to an inlet is deprecated and will be removed in 3.0; send to your outlet and " +
              "connect it with a connector, or use `tell` to deliver directly to a processor",
            code = Option(Messages.DeprecationCode.SendToInlet),
            autoFixable = false
          )
        case _ => ()
      SendStatement(at(start, end), msg, portlet)
    }
  }

  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      Index ~ Keywords.tell ~/ deliverableMessageValue ~/ to ~ processorRef ~
        (by ~/ identifier).? ~/ Index
    )./.map { (start, msg, proc, byId, end) => TellStatement(at(start, end), msg, proc, byId) }
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

  // A28/A17: `when` accepts a structured BooleanExpression, a bare boolean value reference (a single
  // name OR a dotted path -> ValueRef, A17), a pseudo-code LiteralString, or the legacy negated/bare
  // `let`-binding Identifier. ORDER matters:
  //   - `! identifier` (negated) is tried first: `!` is not a boolean-atom start, so the boolean
  //     grammar never consumes it.
  //   - `booleanExprOnly` is tried before `valueRef`: for `when a > b` / `when x and y` / `when true`
  //     it yields a real BooleanExpression; for a BARE atom (`when flag`, `when order.isPaid`) the
  //     filter rejects the bare-atom result and the parse backtracks (no cut before an operator) to
  //     `valueRef`, which builds a first-class ValueRef (A17) covering both a single name and a
  //     dotted path.
  //   - `literalString` then handles the opaque pseudo-code form (`when "user is authenticated"`);
  //     `valueRef` above never consumes a quote, so the order is safe.
  //   - the bare `identifier` arm is the legacy fallback (now effectively unreached, since a bare
  //     name is routed to `valueRef`); kept for AST/API back-compat.
  private def whenCondition[u: P]
    : P[(LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue, Boolean)] = {
    P(
      (Punctuation.exclamation ~ identifier).map(id => (id, true)) |
        booleanExprOnly.map(be => (be, false)) |
        promptValue.map(pv => (pv, false)) |
        valueRef.map(vr => (vr, false)) |
        deprecatedStringCondition.map(ls => (ls, false)) |
        identifier.map(id => (id, false))
    )
  }

  /** A bare string condition — `when "the order has drink items"`.
    *
    * A54 settled that a bare `"x"` is a LITERAL while `prompt("x")` marks a value an AI decides. A
    * natural-language condition is plainly the latter, so spelling it as a bare string contradicts
    * the convention the rest of the language follows. `prompt(...)` is now accepted here and this
    * form is deprecated; it still parses, so no model breaks today.
    */
  private def deprecatedStringCondition[u: P]: P[LiteralString] = {
    P(Index ~ literalString)./.map { case (start, ls) =>
      deprecation(
        at(start, start),
        "A bare string `when` condition is deprecated; use `when prompt(\"...\")` for a condition " +
          "an AI evaluates, or a boolean expression for one the model decides",
        code = Option(Messages.DeprecationCode.BareStringCondition),
        autoFixable = false
      )
      ls
    }
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

  // A29: the subject of a `match` — a `get from input/state` read (keyword-led, tried first), a
  // legacy pseudo-code string, or a bare value reference (`order.status`). NOT a constant (matching
  // a constant subject is pointless). fastparse `|` unifies to RiddlValue, so each branch is widened.
  private def matchSubject[u: P]: P[MatchSubject] = {
    P(
      getValue.map(gv => gv: MatchSubject) |
        literalString.map(ls => ls: MatchSubject) |
        valueRef.map(vr => vr: MatchSubject)
    )
  }

  // A29: a case pattern. ORDER: the comparison arm (an explicit operator + comparand) is tried first
  // so `case == Approved`/`case > MaxCount` become a ComparisonPattern; a quoted string is the legacy
  // LiteralPattern; a bare path is a TypePattern (type-case). The explicit operator on the comparison
  // arm is what disambiguates it from a bare type-case (`case Approved` is a TYPE-case).
  private def matchPattern[u: P]: P[MatchPattern] = {
    P(
      (Index ~ comparisonOperator ~/ comparand ~ Index).map { case (s, op, c, e) =>
        ComparisonPattern(at(s, e), op, c): MatchPattern
      } |
        literalString.map(ls => LiteralPattern(ls.loc, ls): MatchPattern) |
        typeRef.map(tr => TypePattern(tr.loc, tr): MatchPattern)
    )
  }

  // A29: the optional `when` guard of a case. Mirrors A17's `when` condition: a structured
  // BooleanExpression, or a bare boolean-typed value reference (`when active`, `when order.isPaid`).
  // ORDER: `booleanExprOnly` first so `a > b`/`x and y` become a real BooleanExpression; a BARE atom
  // (`active`) fails that filter and backtracks (no cut before an operator) to `valueRef`.
  private def matchGuard[u: P]: P[BooleanExpression | ValueRef] = {
    P(
      booleanExprOnly.map(be => be: BooleanExpression | ValueRef) |
        valueRef.map(vr => vr: BooleanExpression | ValueRef)
    )
  }

  // A29: `case <pattern> [when <guard>] { <statements> }`.
  private def matchCase[u: P](set: StatementsSet): P[MatchCase] = {
    P(
      Index ~ Keywords.case_ ~/ matchPattern ~ (Keywords.when ~/ matchGuard).? ~
        open ~/ setOfStatements(set) ~ close ~/ Index
    )./.map { case (start, pattern, guard, statements, end) =>
      MatchCase(at(start, end), pattern, guard, statements.toContents)
    }
  }

  private def matchStatement[u: P](set: StatementsSet): P[MatchStatement] = {
    P(
      Index ~ Keywords.`match` ~/ matchSubject ~ open ~/
        matchCase(set).rep(1) ~
        (Keywords.default ~ open ~/ setOfStatements(set) ~ close).? ~/
        close ~/ Index
    )./.map { case (start, subject, cases, maybeDefault, end) =>
      val default = maybeDefault.getOrElse(Seq.empty[Statements])
      MatchStatement(at(start, end), subject, cases.toSeq, default.toContents)
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
    // `foreach k, v in m` destructures a mapping into key and value. The comma cannot collide:
    // RIDDL separates statements by whitespace, never by punctuation, so nothing else could be
    // starting here. ARITY IS NOT CHECKED HERE -- a parser `error()` preempts the whole pass chain,
    // so the "one name for a mapping" / "two for anything else" diagnostics live in ValidationPass,
    // which is also the only place that knows the collection's type.
    P(
      Index ~ Keywords.foreach ~/ identifier ~ (Punctuation.comma ~ identifier).? ~ in ~
        foreachCollection ~ open ~/ setOfStatements(set) ~ close ~/ Index
    )./.map { case (start, element, valueElement, collection, statements, end) =>
      ForeachStatement(at(start, end), element, valueElement, collection, statements.toContents)
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
  // `private[parsing]`, not `private`: TypeParser's `constant` rule reuses this directly rather than
  // duplicating it, so a `constant` value can be a prompt hole too.
  private[parsing] def promptValue[u: P]: P[PromptValue] = {
    P(
      Index ~ Keywords.prompt ~ Punctuation.roundOpen ~/ literalString ~ Punctuation.roundClose ~/ Index
    )./.map { case (start, str, end) => PromptValue(at(start, end), str) }
  }

  /** A numeric literal — `[+-]? digits [ . digits ] [ (e|E) [+-] digits ]`.
    *
    * Captured as raw text, not converted: the AST stores what the author wrote. No digit
    * separators and no radix prefixes — declined deliberately (Reid, 2026-08-14); both are pure
    * additions later if wanted.
    *
    * There is no lexical ambiguity with identifiers or paths: an identifier must begin with a
    * letter (`simpleIdentifier`), so nothing beginning with a digit or a sign can be one.
    *
    * **Every digit run uses `CharsWhileIn`, never `CharIn(...).rep(1)`.** Under
    * `MultiLineWhitespace`, `.rep` skips whitespace BETWEEN repetitions regardless of `~~` at the
    * rule's own boundaries — confirmed empirically against fastparse 3.1.1, 2026-08-15. With
    * `CharIn("0-9").rep(1)` this parsed `1 2` as ONE literal of text `"1 2"` (`isInteger` then
    * reporting `true`, and `asLong` throwing `NumberFormatException` instead of the author getting
    * an "expected `,` or `)`" parse error). `CharsWhileIn` is a run primitive with no such gap.
    */
  // `private[parsing]`, not `private`: TypeParser's `constant` rule reuses this directly rather than
  // duplicating it, so a `constant` value can be a bare numeric literal too.
  private[parsing] def numericLiteral[u: P]: P[NumericLiteral] = {
    P(
      Index ~~ (CharIn("+\\-").? ~~ CharsWhileIn("0-9") ~~
        ("." ~~ CharsWhileIn("0-9")).? ~~
        (CharIn("eE") ~~ CharIn("+\\-").? ~~ CharsWhileIn("0-9")).?).! ~~ Index
    ).map { case (start, text, end) => NumericLiteral(at(start, end), text) }
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
        callValue.map(c => c: Value) | // A24: `call function F(args)` (keyword-led)
        askValue.map(a => a: Value) | // `ask query Q of <processor>` (keyword-led)
        initiateValue.map(i => i: Value) | // `initiate <processor>[(args)]` (keyword-led)
        constructor.map(c => c: Value) |
        getValue.map(gv => gv: Value) |
        booleanExpr |
        // LAST, and deliberately: `booleanExpr` must get first refusal. This ordering is now
        // LOAD-BEARING (it was inert when written -- `comparand` accepted only
        // `GetValue | ConstantRef | ValueRef` -- until `comparand` was widened to accept
        // `NumericLiteral`). Trying `booleanExpr` first is what keeps `5 > 3` parsing as a
        // comparison rather than `numericLiteral` matching the bare `5`, returning it as the whole
        // value, and leaving `> 3` dangling: `comparison` cuts only AFTER its operator, so a bare
        // `5` backtracks cleanly out of `booleanExpr` and lands here.
        numericLiteral.map(nl => nl: Value)
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

  // A28: a boolean expression that MUST contain a real operator/literal (i.e. produce an actual
  // `BooleanExpression` node, not a bare atom). `booleanExpr` returns the bare `Value` atom when no
  // operator is present; filtering to `BooleanExpression` makes that bare-atom case FAIL so the
  // enclosing alternation backtracks to the legacy arm. This is the disambiguation that keeps
  // `when someBoolField` an `Identifier` and `require invariant X` an `InvariantRef`, while
  // `when a > b`, `when x and y`, `require count == 0` become structured BooleanExpressions.
  private def booleanExprOnly[u: P]: P[BooleanExpression] = {
    P(booleanExpr).filter(_.isInstanceOf[BooleanExpression]).map(_.asInstanceOf[BooleanExpression])
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

  // comparison level (non-associative). A comparison's two operands are `comparand` — a TYPED ref
  // OR a bare numeric literal (A28, widened 2026-08-14); a quoted string, a constructor and a
  // boolean literal are still not comparands. So `count > "5"` / `count > true` / `count > R(1)`
  // FAIL to parse (the `~/` cut after the operator commits, and the right operand must match
  // `comparand`), while `count > 5` now parses -- and draws a StyleWarning in validation rather
  // than a parse error. When there is NO operator the bare boolean ATOM is returned unchanged (NOT
  // wrapped) — a comparand parsed as the left operand with no operator following backtracks (no cut
  // before the operator) and re-parses via `booleanAtom`, so `true`, `(a and b)`, and a bare
  // boolean-typed ref remain valid standalone atoms.
  private def comparison[u: P]: P[Value] = {
    P(
      (Index ~ comparand ~ comparisonOperator ~/ comparand ~ Index).map {
        case (start, left, op, right, end) =>
          ComparisonExpression(at(start, end), op, left, right): Value
      } | booleanAtom
    )
  }

  // A28, widened 2026-08-14: a comparison operand — a TYPED reference, OR a bare numeric literal.
  // `get from …` and `constant <path>` are keyword-led (tried first); `numericLiteral` goes next so
  // `count > 5` parses the digits as a literal rather than falling through to `valueRef`, which
  // would try (and fail) to resolve "5" as a path; a bare path is a `ValueRef` (which may itself
  // resolve to a `Constant` at validation, so `count > MaxCount` still works) and stays LAST — it is
  // the permissive fallback. Originally this rule banned literals outright ("magic-constant
  // comparisons cannot be constructed at all"); Reid reversed that 2026-08-14 (see the doc on
  // `AST.Comparand`) because the corpus held exactly ONE named constant across 189 models, so the
  // ban had no uptake to protect. The intent survives as a StyleWarning in validation, not a parse
  // error. `!booleanLiteral` still rejects `true`/`false` as operands (they are boolean ATOMS, not
  // comparands) so `count > true` remains a parse error, while a field named `trueValue`
  // (word-boundary) is still a legal ref.
  private def comparand[u: P]: P[Comparand] = {
    P(
      getValue.map(gv => gv: Comparand) |
        constantRef.map(cr => cr: Comparand) |
        numericLiteral.map(nl => nl: Comparand) |
        (!booleanLiteral ~ valueRef).map(vr => vr: Comparand)
    )
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
  // `private[parsing]`, not `private`: TypeParser's `constant` rule reuses this directly rather than
  // duplicating it, so a `constant` value can be a bare boolean literal too.
  private[parsing] def booleanLiteral[u: P]: P[BooleanLiteral] = {
    P(
      Index ~ (Keywords.keyword("true").map(_ => true) | Keywords
        .keyword("false")
        .map(_ => false)) ~ Index
    ).map { case (start, b, end) => BooleanLiteral(at(start, end), b) }
  }

  // A28: an atom of the boolean-expression sub-language: a boolean literal (`true`/`false`), a
  // parenthesized boolean expression (for grouping / precedence override), or a bare boolean-typed
  // reference (`get from …` or a bare path). Comparison operands are NOT parsed here — they are
  // `comparand` (refs plus a bare `NumericLiteral`, since Reid reversed A28's ref-only rule
  // 2026-08-14); a boolean atom is the operand of `and`/`or`/`not` or a standalone boolean.
  // `booleanLiteral` precedes `valueRef` so `true`/`false` are literals here; `valueRef` stays last
  // (permissive bare path). Non-boolean value atoms (literal strings, constructors, prompt values)
  // are handled by `value` directly, before the boolean sub-language, so they never reach here.
  /** `invariant X` / `invariant X with <expr>` as a boolean atom.
    *
    * MUST precede `valueRef` below: `valueRef` would happily take `invariant` as an ordinary
    * identifier, which is exactly the mis-parse this fixes — the author got "expected a comparison
    * operator" pointing at the END of the keyword.
    */
  private def invariantCondition[u: P]: P[InvariantCondition] = {
    P(
      Index ~ Keywords.invariant ~/ pathIdentifier ~ (Keywords.`with` ~ value).? ~ Index
    ).map { case (start, pid, arg, end) =>
      val loc = at(start, end)
      InvariantCondition(loc, InvariantRef(loc, pid), arg)
    }
  }

  private def booleanAtom[u: P]: P[Value] = {
    P(
      booleanLiteral.map(bl => bl: Value) |
        (Punctuation.roundOpen ~ booleanExpr ~ Punctuation.roundClose) |
        getValue.map(gv => gv: Value) |
        invariantCondition.map(ic => ic: Value) |
        selfValue.map(sv => sv: Value) | // before valueRef: `self` is a keyword, not a path
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

  // A24: `call function <path>(<args>)` — call a pure function to get its result value. `functionRef`
  // consumes the leading `function` keyword; args reuse `constructorArg` (positional then named).
  // "Functions only" is enforced by the `functionRef` target; empty `()` is allowed (no-input function).
  private def callValue[u: P]: P[Call] = {
    P(
      Index ~ Keywords.call ~/ functionRef ~
        Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
        Punctuation.roundClose ~/ Index
    )./.map { case (start, fnRef, args, end) =>
      Call(at(start, end), fnRef, args.toSeq)
    }
  }

  /** `ask query Foo of entity Bar` -- a request whose answer is a value.
    *
    * The operand is a `queryRef` SPECIFICALLY, not a general messageRef, so "ask takes a query" is
    * structural: asking a command cannot be built, only mis-parsed, and the resulting message
    * names the shape the author actually wrote. Validation still reports an unresolved query and a
    * query that declares no `replies`, since neither is decidable here.
    */
  private def askValue[u: P]: P[Ask] = {
    P(
      Index ~ Keywords.ask ~/ queryRef ~ of ~/ processorRef ~/ Index
    )./.map { case (start, qRef, pRef, end) => Ask(at(start, end), qRef, pRef) }
  }

  /** `initiate <processor>[(args)]` -- bring an instance into being and yield its identity.
    *
    * Parens are OPTIONAL and present exactly when there are arguments (Reid, 2026-08-13: one
    * keyword, not two). ARITY IS NOT CHECKED HERE -- a parser error() preempts the whole pass
    * chain, so the argument diagnostics live in ValidationPass, which is also the only place
    * that has resolved `on init`.
    */
  private def initiateValue[u: P]: P[Initiate] = {
    P(
      Index ~ Keywords.initiate ~/ processorRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~/ Index
    )./.map { case (start, pRef, args, end) =>
      Initiate(at(start, end), pRef, args.map(_.toSeq).getOrElse(Seq.empty))
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

  // `self` -- the running processor instance. `self.id` is parsed as ONE value rather than as a
  // path walk, because the anchor is a keyword and not a name in scope; the FIELD then types
  // through the synthesized aggregation, which is what lets `let me = self; me.id` work.
  // MUST precede `valueRef` in `booleanAtom`: `self` is not a `definitionKeywords` entry, so
  // `valueRef`'s permissive bare-path parser would otherwise happily consume it as an ordinary
  // identifier.
  private def selfValue[u: P]: P[SelfValue] = {
    P(
      Index ~ Keywords.self ~ (Punctuation.dot ~ identifier).? ~ Index
    )./.map { case (start, field, end) => SelfValue(at(start, end), field) }
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

  /** `terminate <processor>[(args)]` -- end an instance by invoking its `on term`.
    *
    * Parentheses are OPTIONAL, exactly as `initiate`'s are. They were mandatory between the
    * instance-identity branch's final review and 2026-08-14 because `on term`'s leading `Id(...)`
    * parameter was REQUIRED, which made a no-argument `terminate` unable to satisfy the arity
    * check and therefore unreachable in any valid model -- a spelling that always failed
    * validation. Reid dropped that requirement (`self.id` is live for the whole clause, so the
    * parameter restated what the language already supplies), which removed the sole justification
    * for the asymmetry, so the bare form came back with it.
    *
    * `terminate P()` still parses: the empty list is accepted rather than made an error, since the
    * grammar is not the place to encode arity.
    *
    * ARITY IS NOT CHECKED HERE -- a parser error() preempts the whole pass chain, so the argument
    * diagnostics live in ValidationPass, which is also the only place that has resolved `on term`.
    */
  private def terminateStatement[u: P]: P[TerminateStatement] = {
    P(
      Index ~ Keywords.terminate ~/ processorRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~ Index
    )./.map { case (start, pRef, args, end) =>
      TerminateStatement(at(start, end), pRef, args.map(_.toSeq).getOrElse(Seq.empty))
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
        putStatements(set) | returnStatements(set) | terminateStatement |
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
