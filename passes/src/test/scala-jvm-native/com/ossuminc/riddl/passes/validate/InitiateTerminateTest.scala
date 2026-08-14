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

/** `initiate` mints an instance; `terminate` ends one.
  *
  * Neither contradicts activate-on-first-message (CM line 999): construction still completes
  * only when `on init` finishes, and what was missing was the invocation. The codebase already
  * partitions the two -- `on init` is once-ever, `on activate` is per-rehydration.
  *
  * The model's argument uses a `String` field with quoted literals ("1"/"2"), NOT `Integer` with
  * bare `1`/`2` as task-4-brief.md originally sketched: RIDDL has no bare-number Value production
  * today (confirmed empirically -- `count > 5` and `record R(1)` both fail to PARSE; see
  * `StatementsTest`'s "reject a bare-number comparison operand" case). `checkArgumentTypes` skips
  * deep type-checking for a primitive (non-aliased) field type either way, so the switch to
  * `String` changes nothing about what these tests actually exercise (arity, not type-checking).
  */
class InitiateTerminateTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(orderInit: String, callerBody: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "c" }
       |    record R is { total: String } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler OH is { $orderInit } with { briefly "oh" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $callerBody } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** A `???` target for the stub-exemption cases below. */
  private def stubModel(callerBody: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "c" }
       |    record R is { total: String } with { briefly "r" }
       |    entity Order is { ??? } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $callerBody } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "initiate" should {
    "accept matching arguments and yield an Id" in { (td: TestData) =>
      diagnostics(
        model("""on init(total: String) is { do "start" }""",
              """let oid = initiate entity Order("1")"""),
        "initiate-ok"
      ).justErrors mustBe empty
    }

    "accept the no-parens form when on init takes nothing" in { (td: TestData) =>
      diagnostics(
        model("""on init is { do "start" }""", """let oid = initiate entity Order"""),
        "initiate-bare"
      ).justErrors mustBe empty
    }

    "REJECT the wrong argument count" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init(total: String) is { do "start" }""",
              """let oid = initiate entity Order("1", "2")"""),
        "initiate-arity"
      ).justErrors.map(_.message).mkString("\n")
      text must include("2")
      text must include("1")
    }

    "REJECT parens where on init declares no parameters" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init is { do "start" }""", """let oid = initiate entity Order("1")"""),
        "initiate-extra"
      ).justErrors.map(_.message).mkString("\n")
      text must include("no parameters")
    }

    // Regression guard for Task 2's Critical finding: `checkInitiate` is reached from the VALUE
    // path (`validateValue`), which `checkStatementScopes` recurses into for a `WhenStatement`'s
    // `thenStatements`/`elseStatements` -- but `validateStatement`'s generic dispatch does NOT
    // descend into those (they are FIELDS, not `contents`). Task 2 shipped exactly this shape of
    // bug once already (self checks reachable only at the top level of an on-clause); this proves
    // the placement is correct today AND stays correct if a future refactor moves the check.
    "REJECT the wrong argument count when 'initiate' is nested inside a 'when' block" in {
      (td: TestData) =>
        val text = diagnostics(
          model(
            """on init(total: String) is { do "start" }""",
            """when true then { let oid = initiate entity Order("1", "2") } end"""
          ),
          "initiate-nested-when"
        ).justErrors.map(_.message).mkString("\n")
        text must include("2")
        text must include("1")
    }
  }

  "terminate" should {
    "accept a leading id argument" in { (td: TestData) =>
      diagnostics(
        model("""on init is { do "start" }
                |          on term(oid: Id(entity Order)) is { do "end" }""".stripMargin,
              """let oid = initiate entity Order
                  |            terminate entity Order(oid)""".stripMargin),
        "terminate-ok"
      ).justErrors mustBe empty
    }

    "REJECT arguments that do not match on term" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init is { do "start" }
                |          on term(oid: Id(entity Order)) is { do "end" }""".stripMargin,
              """terminate entity Order()"""),
        "terminate-arity"
      ).justErrors.map(_.message).mkString("\n")
      text must include("1")
    }

    // Regression guard mirroring `initiate`'s: `checkTerminate` is reached from
    // `checkStatementScopes`, the single entry point invoked at every container root AND
    // recursively for a `WhenStatement`'s `thenStatements`/`elseStatements` -- but
    // `validateStatement`'s generic dispatch does NOT descend into those (they are FIELDS, not
    // `contents`). Proves the placement is correct today AND stays correct if a future refactor
    // moves the check.
    "REJECT arguments that do not match on term when 'terminate' is nested inside a 'when' block" in {
      (td: TestData) =>
        val text = diagnostics(
          model(
            """on init is { do "start" }
              |          on term(oid: Id(entity Order)) is { do "end" }""".stripMargin,
            """when true then { terminate entity Order() } end"""
          ),
          "terminate-nested-when"
        ).justErrors.map(_.message).mkString("\n")
        text must include("1")
    }

    // Reid's ruling, final review: the bare `terminate P` spelling was REMOVED -- it parsed but
    // could never validate, because `on term`'s leading Id(...) parameter is mandatory. The parse
    // rejection is pinned in `TerminateFileTest`; `terminate P()` (an explicitly empty list)
    // remains writable and is what the arity cases above report on.
  }

  /** The standing `???` ruling: a definition whose body is `???` has said "don't expect much", so
    * every check other than "provide a body" is skipped for it. `checkTellAddressing` already
    * gated this way; `checkInitiate`/`checkTerminate` did not, so invoking a stub with arguments
    * drew a hard Error reasoning from an unwritten body -- exactly the inference the ruling
    * forbids. Both directions are pinned: the stub is exempt, and a REAL body is still checked
    * (an exemption with no counter-example is indistinguishable from one applied too widely).
    */
  "a `???` target" should {
    "exempt `initiate` from the arity check" in { (td: TestData) =>
      diagnostics(stubModel("""let oid = initiate entity Order("1")"""), "initiate-stub")
        .justErrors mustBe empty
    }

    "exempt `terminate` from the arity check" in { (td: TestData) =>
      diagnostics(stubModel("""terminate entity Order("1")"""), "terminate-stub")
        .justErrors mustBe empty
    }

    "still validate the ARGUMENT VALUES at the call site" in { (td: TestData) =>
      // The exemption is about the CALLEE. A name written at the call site either exists or it
      // does not, and the callee being a stub says nothing about that.
      val text = diagnostics(stubModel("""terminate entity Order(nosuchlocal)"""), "stub-args")
        .justErrors.map(_.message).mkString("\n")
      text must include("nosuchlocal")
    }
  }
}
