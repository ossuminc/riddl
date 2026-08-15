/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `initiate` and `terminate` are EFFECTS, so effect bans apply to them exactly as they apply to
  * `tell`/`send`/`set`/etc: banned in a pure function body, banned in an `on activate`/
  * `on passivate` clause (which must be side-effect-free), and banned in a projector correlation
  * fold (which must be pure so re-running it over the same events is safe -- A70 §6.5).
  *
  * The fold ban for `terminate` already existed (Task 5, `validateCorrelation`); this task's real
  * job was closing the gap that left `initiate` unbanned there (it is a VALUE, hiding inside a
  * `let`, not a `Statement` the old switch matched on) and adding the other two bans, which did not
  * exist at all.
  *
  * Each ban is paired with a POSITIVE case. Without the positive half, a ban wrongly applied to
  * everything would still look green -- the lesson from A70, where "legal in the timeout block" was
  * the case that mattered. The fold case additionally asserts an EXACT error count, not merely a
  * substring match, because two independent checks now touch a fold (this task's own scope checks,
  * and the extended `validateCorrelation` fold-purity check) and a substring match cannot see a
  * duplicate report.
  */
class InstanceEffectBanTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsIn(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** Every model below declares the same `entity Order` so `initiate entity Order` resolves, and
    * declares `on term` so `terminate oid` resolves too; only the CONTEXT the
    * offending statement sits in differs, which is the variable under test.
    */
  private def wrap(inner: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    event Started is { oid: Id(entity Order) } with { briefly "ev" }
       |    command Record is { oid: Id(entity Order) } with { briefly "cmd" }
       |    record R is { total: Integer } with { briefly "r" }
       |    invariant Inv is "always" with { briefly "inv" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is {
       |          on command Go { do "go" }
       |          on term is { do "end" }
       |        } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |$inner
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def functionModel(stmt: String): String = wrap(
    s"""    function F is {
       |      requires { a: Integer }
       |      returns { b: Integer }
       |      $stmt
       |      return a
       |    } with { briefly "fn" }""".stripMargin)

  private def entityModel(stmt: String): String = wrap(
    s"""    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $stmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }""".stripMargin)

  private def activateModel(stmt: String): String = wrap(
    s"""    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on activate is { $stmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }""".stripMargin)

  // Review round 1 addendum: `on passivate` shares `checkInstanceEffectScope`'s match arm (and
  // its message) with `on activate` -- one case pins that the claim is actually true for both,
  // not merely for the one the original brief happened to test.
  private def passivateModel(stmt: String): String = wrap(
    s"""    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on passivate is { $stmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }""".stripMargin)

  // `set field oid to e.oid` keeps the fold clause itself unobjectionable -- `oid` is the
  // correlation's only key field, so no fold is even REQUIRED to set it for the yielded command to
  // be completable, but every fold handler must terminate in SOME `set` or a separate, unrelated
  // rule ("every fold must terminate in a 'set'") fires and would otherwise pollute these cases.
  private def foldModel(stmt: String): String = wrap(
    s"""    repository Repo is {
       |      handler RH is { on command Record { do "save" } } with { briefly "rh" }
       |    } with { briefly "repo" }
       |    projector Proj is {
       |      updates repository Repo
       |      correlation Corr by oid yields command Record is {
       |        handler CollectH is {
       |          on e: event Started is {
       |            $stmt
       |            set field oid to e.oid
       |          }
       |        } with { briefly "folds" }
       |      } times out after "1 hour" { do "give up" }
       |    } with { briefly "proj" }""".stripMargin)

  private def timeoutModel(stmt: String): String = wrap(
    s"""    repository Repo is {
       |      handler RH is { on command Record { do "save" } } with { briefly "rh" }
       |    } with { briefly "repo" }
       |    projector Proj is {
       |      updates repository Repo
       |      correlation Corr by oid yields command Record is {
       |        handler CollectH is {
       |          on e: event Started is { set field oid to e.oid }
       |        } with { briefly "folds" }
       |      } times out after "1 hour" { let oid = initiate entity Order
       |                                   $stmt }
       |    } with { briefly "proj" }""".stripMargin)

  /** Task 6's fixture is standalone rather than built on [[wrap]]: `wrap`'s `entity Order`
    * deliberately declares NO `on init`, which the ban cases rely on (they only ever assert that a
    * ban fired), while a case asserting ZERO errors needs a target that can actually be initiated.
    * Adding `on init` to `wrap` would have changed the arity every other case sends through it.
    *
    * The second, inert step is not padding: a Saga must declare at least two (`Sagas must define at
    * least 2 steps`), so a one-step model would fail this case for a reason unrelated to the ruling.
    */
  private def sagaModel(stmt: String): String =
    s"""domain SDom is {
       |  context SCtx is {
       |    record SR is { total: String } with { briefly "r" }
       |    entity Worker is {
       |      state WS of record SR is {
       |        handler WH is {
       |          on init(total: String) is { do "start" }
       |          on term is { do "end" }
       |        } with { briefly "wh" }
       |      } with { briefly "ws" }
       |    } with { briefly "we" }
       |    saga Flow is {
       |      requires { why: String }
       |      returns { out: String }
       |      step One is {
       |        $stmt
       |      } reverted by {
       |        do "undo"
       |      } with { briefly "step" }
       |      step Two is {
       |        do "carry on"
       |      } reverted by {
       |        do "undo"
       |      } with { briefly "step" }
       |    } with { briefly "saga" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "initiate/terminate" should {
    "be BANNED in a function body" in { (td: TestData) =>
      errorsIn(functionModel("""let x = initiate entity Order"""), "ban-fn") must
        include("function")
    }

    // Nesting regression: proves the ban is enforced by `checkStatementScopes` (which recurses into
    // when/match/foreach FIELD-held statement lists), not by `validateStatement` (which does not).
    "be BANNED in a function body, nested inside a 'when' block" in { (td: TestData) =>
      errorsIn(
        functionModel("""when true then { let x = initiate entity Order } end"""),
        "ban-fn-nested"
      ) must include("function")
    }

    // Review round 1 addendum: the original four cases exercised `initiate` only in both NEW
    // contexts. `terminateStatement` is parsed unconditionally by `anyDefStatements`, and neither
    // `ProcessorKind.Function` nor `ClauseRestriction.ActivationClause` suppresses it in
    // `StatementParser.statement` -- so `terminate` in a function body / activation clause is a
    // live, parseable path whose ban rested entirely on the `TerminateStatement` match arm no test
    // reached. `terminate` needs a bound instance to name, so bind one with `let`/`initiate` first,
    // the same shape `timeoutModel` already uses.
    "be BANNED (terminate) in a function body" in { (td: TestData) =>
      val errors = errorsIn(
        functionModel(
          """let x = initiate entity Order
            |      terminate x""".stripMargin
        ),
        "ban-fn-terminate"
      )
      errors must include("function")
      errors must include("terminate")
    }

    "be LEGAL in an ordinary entity handler" in { (td: TestData) =>
      errorsIn(entityModel("""let x = initiate entity Order"""), "ok-entity") mustBe ""
    }

    "be BANNED in an on activate clause" in { (td: TestData) =>
      errorsIn(activateModel("""let x = initiate entity Order"""), "ban-activate") must
        include("activat")
    }

    "be BANNED in an on activate clause, nested inside a 'when' block" in { (td: TestData) =>
      errorsIn(
        activateModel("""when true then { let x = initiate entity Order } end"""),
        "ban-activate-nested"
      ) must include("activat")
    }

    "be BANNED (terminate) in an on activate clause" in { (td: TestData) =>
      val errors = errorsIn(
        activateModel(
          """let x = initiate entity Order
            |      terminate x""".stripMargin
        ),
        "ban-activate-terminate"
      )
      errors must include("activat")
      errors must include("terminate")
    }

    "be BANNED in an on passivate clause" in { (td: TestData) =>
      // Pins the claim that the ban applies to BOTH clauses the message names, not just the one
      // ("on activate") every other case here happens to use.
      errorsIn(passivateModel("""let x = initiate entity Order"""), "ban-passivate") must
        include("activat")
    }

    // Important #1 (review round 1): `statementValues` used to DROP `RequireStatement.argument`
    // (the `with <expr>` operand) -- a full Value, parsed by the same `value` rule that admits
    // `initiate`, and `require` is legal in both a function body and an activation clause
    // (`guardStatements` suppresses it only under `EventClause`). So `require true with initiate
    // entity Order` evaded `checkInstanceEffectScope` (and the fold-purity walk) entirely before
    // the fix. Exercised here in a function body, where the ban applies.
    "be BANNED when 'initiate' hides inside a 'require ... with' operand" in { (td: TestData) =>
      errorsIn(
        functionModel("""require true with initiate entity Order"""),
        "ban-fn-require-with"
      ) must include("function")
    }

    // Important #1, second half: `MatchCase.guard` is the same shape and was equally unfed. The
    // ONLY way `initiate` can reach a guard at all is through `invariant X with initiate ...` --
    // `matchGuard`'s grammar bottoms out at `booleanAtom`, whose sole value-carrying member is
    // `invariantCondition` (`StatementParser.booleanAtom`); the parser's `andExpr`/`comparison`
    // operands are typed refs (`Comparand`), never `initiate`. So this is not an arbitrary
    // fixture -- it is the one shape that actually parses.
    "be BANNED when 'initiate' hides inside a MatchCase guard" in { (td: TestData) =>
      errorsIn(
        functionModel(
          """match a {
            |        case "x" when invariant Inv with initiate entity Order {
            |          do "case"
            |        }
            |      }""".stripMargin
        ),
        "ban-fn-match-guard"
      ) must include("function")
    }

    "be BANNED in a projector correlation fold" in { (td: TestData) =>
      val errors = errorsIn(foldModel("""let x = initiate entity Order"""), "ban-fold")
      errors must include("fold")
      // Exactly one error for the one offending statement -- proves `checkInstanceEffectScope`
      // (wired via `checkStatementScopes`, reached generically for every `OnClause` including a
      // correlation's `on event` clauses) does NOT also fire here and double the report; only the
      // fold-purity check in `validateCorrelation` owns this defect.
      errors.linesIterator.count(_.nonEmpty) mustBe 1
    }

    "be BANNED (terminate) in a projector correlation fold, exactly once" in { (td: TestData) =>
      // The asymmetric half Task 5 left: `terminate` in a fold was ALREADY banned before this task.
      // This case is the regression proof that extending the same check site for `initiate` did not
      // turn `terminate`'s existing single error into two.
      val errors = errorsIn(
        foldModel("""terminate e.oid"""),
        "ban-fold-terminate"
      )
      errors must include("fold")
      errors must include("terminate")
      errors.linesIterator.count(_.nonEmpty) mustBe 1
    }

    "be LEGAL in a correlation timeout block" in { (td: TestData) =>
      // The timeout block EXISTS to have an effect (design spec §6.7), so banning it there
      // would leave it useless. This is the case that distinguishes a correct ban.
      errorsIn(timeoutModel("""terminate oid"""), "ok-timeout") mustBe ""
    }

    // Important #2 (review round 1): before `Correlation.timeoutStatements` was wired into
    // `checkStatementScopes`, the case immediately above could not fail -- a timeout block never
    // reached ANY check that lives only in `checkStatementScopes` (checkInitiate/checkTerminate
    // arity+type, let-scope threading, tell addressing), so "legal" and "unchecked" were
    // indistinguishable. This proves the wiring is real: an arity mismatch against `on term`,
    // which declares NO payload parameters, supplied with one argument, is now reported --
    // something that would have gone completely unreported before this fix, since
    // `checkTerminate` never ran for a timeout block at all.
    "now runs full statement validation inside a correlation timeout block" in { (td: TestData) =>
      val errors = errorsIn(
        timeoutModel("""terminate oid with (oid)"""),
        "timeout-arity-now-checked"
      )
      errors must include("on term")
      errors must include("no parameters")
    }

    /** Message-value-source plan, Task 6. Reid, 2026-08-14: `initiate`/`terminate` ARE legal in a
      * saga step, because a saga may need to create the entities it coordinates.
      *
      * That is already the behaviour, but only **by accident**: `checkInstanceEffectScope`'s two
      * predicates are structurally false for a saga step — a `SagaStep` is a `Leaf`, never pushed
      * onto the parent stack (see `Pass.traverse`), so `parents.head` is the Saga, which is neither
      * an activation clause nor a `Function`. Nothing states the ruling, so a future tightening
      * that added a Saga arm "for symmetry" would silently remove it and no test would notice.
      *
      * The two are exercised TOGETHER in one step on purpose: the pair is what a saga actually
      * needs (create, then unwind), and using `wid` also keeps Task 5's unused-id warning out of
      * the picture so a failure here can only mean the ban misfired.
      */
    "be LEGAL in a saga step (Reid's ruling, 2026-08-14)" in { (td: TestData) =>
      errorsIn(
        sagaModel("""let wid = initiate entity Worker("1")
                    |        terminate wid""".stripMargin),
        "ok-saga-step"
      ) mustBe ""
    }
  }
}
