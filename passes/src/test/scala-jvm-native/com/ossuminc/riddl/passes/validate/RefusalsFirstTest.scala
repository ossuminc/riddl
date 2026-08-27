/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A23 ("refusals first"): within a single linear statement list, no EFFECT statement (set/morph/
  * become/send/tell/yield/put) may appear before a REFUSAL (require/error). Each statement list is
  * checked independently (Option A — per-list); nested when/match/foreach branch bodies are their
  * own lists. Applies to handler on-clauses and to a saga step's do-statements (undo is NOT
  * checked).
  */
class RefusalsFirstTest extends AbstractValidatingTest {

  private val a23Text = "must come before any effect"

  /** Wrap an on-clause body in a valid entity so paths resolve; return the count of A23 messages.
    */
  private def onClauseA23Count(body: String, td: TestData): Int = {
    val input = RiddlParserInput(
      s"""domain d is {
         |  context c is {
         |    command DoIt is { x: Integer }
         |    event Ev is { a: Integer }
         |    entity e is {
         |      source src is { outlet O is event Ev }
         |      type SF is { f: String }
         |      state S of record SF
         |      handler h is {
         |        on command DoIt {
         |          $body
         |        }
         |      }
         |    }
         |  }
         |}
         |""".stripMargin,
      td
    )
    var count = 0
    parseAndValidateDomain(input, shouldFailOnErrors = false) {
      case (_, _, msgs: Messages.Messages) =>
        count = msgs.count(m => m.kind == Messages.Error && m.message.contains(a23Text))
        succeed
    }
    count
  }

  "A23 refusals-first (on-clause)" should {

    "accept refusals before an effect (clean)" in { (td: TestData) =>
      onClauseA23Count(
        """require "authorized"
          |error "still may refuse"
          |set field S.f to "x"""".stripMargin,
        td
      ) mustBe 0
    }

    "reject a require after an effect (set then require)" in { (td: TestData) =>
      onClauseA23Count(
        """set field S.f to "x"
          |require "too late"""".stripMargin,
        td
      ) mustBe 1
    }

    // REVERSED 2026-08-19 by the author's ruling narrowing A23 to LOCAL state transformation.
    // A `send` leaves nothing partial HERE -- any state it causes is elsewhere and later -- so it
    // is no longer an effect for this rule, and `send` then `error` is legal.
    //
    // This is not a softening for its own sake: it is what makes the new "error is terminal" rule
    // migratable. The corpus idiom is "refuse AND publish a rejection event" (268 sites in
    // reactive-bbq); with `error` terminal the send must move BEFORE it, and under the old effect
    // set that was itself an A23 error -- so neither order was legal and the idiom became
    // inexpressible. See ErrorTerminalTest.
    "ACCEPT a transmission before a refusal (send then error)" in { (td: TestData) =>
      onClauseA23Count(
        """send event Ev to outlet O
          |error "reject"""".stripMargin,
        td
      ) mustBe 0
    }

    "check each nested branch as its own list (effect then error inside a when)" in {
      (td: TestData) =>
        onClauseA23Count(
          """when "cond" then
            |  set field S.f to "x"
            |  error "no"
            |end""".stripMargin,
          td
        ) mustBe 1
    }

    "not flag a refusal nested in a branch after a top-level effect (Option A per-list)" in {
      (td: TestData) =>
        onClauseA23Count(
          """set field S.f to "x"
            |when "cond" then
            |  error "different list"
            |end""".stripMargin,
          td
        ) mustBe 0
    }

    "not treat an opaque code statement as an effect" in { (td: TestData) =>
      onClauseA23Count(
        """```scala
          |// arbitrary opaque code
          |```
          |require "still fine"""".stripMargin,
        td
      ) mustBe 0
    }
  }

  /** Build a saga whose single step uses the given do/undo bodies; return the count of A23
    * messages.
    */
  private def sagaA23Count(doBody: String, undoBody: String, td: TestData): Int = {
    val input = RiddlParserInput(
      s"""domain d is {
         |  context c is {
         |    command Go is { xfield: Integer }
         |    command UndoGo is { xfield: Integer }
         |    entity e is { sink tank is { inlet inn is command Go } }
         |    saga sag is {
         |      step StepOne is { $doBody } reverted by { $undoBody }
         |      step StepTwo is { do "noop" } reverted by { do "undo noop" }
         |    }
         |  }
         |}
         |""".stripMargin,
      td
    )
    var count = 0
    parseAndValidateDomain(input, shouldFailOnErrors = false) {
      case (_, _, msgs: Messages.Messages) =>
        count = msgs.count(m => m.kind == Messages.Error && m.message.contains(a23Text))
        succeed
    }
    count
  }

  "A23 refusals-first (saga step)" should {

    // The EFFECT here is `terminate`, not the `send` this case used before 2026-08-19. A `send` is
    // no longer an A23 effect (see the on-clause case above), and flipping this to 0 would have
    // made it indistinguishable from its companion below -- both would assert 0, and nothing would
    // still prove that saga do-statements are checked AT ALL. `terminate` keeps the case honest:
    // it remains an effect (ending an instance is the most complete state change there is) and a
    // saga step is permitted to use one.
    "reject an effect before a refusal in do-statements" in { (td: TestData) =>
      sagaA23Count(
        """terminate self.id error "no"""",
        """send command UndoGo to inlet d.c.e.tank.inn""",
        td
      ) mustBe 1
    }

    "not check undo-statements (compensation is out of scope)" in { (td: TestData) =>
      sagaA23Count(
        """send command Go to inlet d.c.e.tank.inn""",
        """send command UndoGo to inlet d.c.e.tank.inn error "no"""",
        td
      ) mustBe 0
    }
  }
}
