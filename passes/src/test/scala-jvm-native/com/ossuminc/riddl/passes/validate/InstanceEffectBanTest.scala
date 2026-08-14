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
    * declares `on term` so `terminate entity Order(oid)` resolves too; only the CONTEXT the
    * offending statement sits in differs, which is the variable under test.
    */
  private def wrap(inner: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    event Started is { oid: Id(entity Order) } with { briefly "ev" }
       |    command Record is { oid: Id(entity Order) } with { briefly "cmd" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is {
       |          on command Go { do "go" }
       |          on term(oid: Id(entity Order)) is { do "end" }
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
        foldModel("""terminate entity Order(e.oid)"""),
        "ban-fold-terminate"
      )
      errors must include("fold")
      errors must include("terminate")
      errors.linesIterator.count(_.nonEmpty) mustBe 1
    }

    "be LEGAL in a correlation timeout block" in { (td: TestData) =>
      // The timeout block EXISTS to have an effect (design spec §6.7), so banning it there
      // would leave it useless. This is the case that distinguishes a correct ban.
      errorsIn(timeoutModel("""terminate entity Order(oid)"""), "ok-timeout") mustBe ""
    }
  }
}
