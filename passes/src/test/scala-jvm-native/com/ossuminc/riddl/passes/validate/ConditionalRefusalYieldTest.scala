/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A command's `yields` contract must be settled on EVERY path, not merely somewhere.
  *
  * `checkYieldConformance` used to ask only "does an `error` or `require` appear ANYWHERE in this
  * clause?", via `Finder.recursiveFindByType`, which searches the whole nested tree. One refusal in
  * one branch therefore exempted the entire clause -- so a handler that refused on one path and
  * produced NOTHING on the other validated completely clean, silently leaving the command's
  * declared event unrecorded.
  *
  * Making `else`/`default` mandatory in the grammar was considered and rejected (Reid, 2026-08-07)
  * on migration cost. Note that an EMPTY `else { }` is not the escape it appears to be -- an empty
  * pseudo-code block is a parse error, so it cannot be written. The escape that does exist, and
  * that this pins, is an `else` that is non-empty but neither yields nor refuses.
  */
class ConditionalRefusalYieldTest extends AbstractValidatingTest {

  private def model(clauseBody: String): String =
    s"""domain Dom is {
       |  context Ours is {
       |    event Paid is { amt: Integer } with { briefly "e" }
       |    event Rejected is { why: String } with { briefly "rj" }
       |    command Pay yields event Dom.Ours.Paid is {
       |      amt: Integer, flagged: Boolean, tags: String*
       |    } with { briefly "c" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Ledger is {
       |      state S of record Dom.Ours.R is {
       |        handler H is {
       |          on p: command Dom.Ours.Pay is {
       |            $clauseBody
       |          }
       |        } with { briefly "h" }
       |      } with { briefly "st" }
       |    } with { briefly "l" }
       |  } with { briefly "ctx" }
       |} with { briefly "dom" }
       |""".stripMargin

  /** Validates and returns formatted errors. Asserts the model PARSED, so a fixture typo shows up
    * as a parse failure rather than masquerading as a missing diagnostic.
    */
  private def errorsFor(body: String, origin: String): String =
    var captured = ""
    parseAndValidate(model(body), origin, shouldFailOnErrors = false) { (_, _, messages) =>
      val parseFailures = messages.justErrors.filter(_.message.startsWith("Expected"))
      withClue(s"fixture did not parse:\n${parseFailures.format}\n") {
        parseFailures mustBe empty
      }
      captured = messages.justErrors.format
      succeed
    }
    captured

  private val notYielded = "does not yield it on every path"

  "a clause that refuses only inside a `when`" should {

    "be an error -- the other path yields nothing" in { (td: TestData) =>
      errorsFor("""when p.flagged then error "refused" end""", td.name) must include(notYielded)
    }

    "still be an error when the else neither yields nor refuses" in { (td: TestData) =>
      // THE load-bearing case. A grammar-level mandatory `else` would not have caught this: the
      // else is present and non-empty, and still produces no event on that path.
      errorsFor(
        """when p.flagged then error "refused" else do "log it" end""",
        td.name
      ) must include(notYielded)
    }
  }

  "a clause that settles the obligation on every path" should {

    "be clean when it refuses unconditionally" in { (td: TestData) =>
      errorsFor("""error "always refused"""", td.name) mustNot include(notYielded)
    }

    "be clean when it yields unconditionally" in { (td: TestData) =>
      errorsFor("yield event Dom.Ours.Paid", td.name) mustNot include(notYielded)
    }

    "be clean when one branch refuses and the other yields" in { (td: TestData) =>
      errorsFor(
        """when p.flagged then error "refused" else yield event Dom.Ours.Paid end""",
        td.name
      ) mustNot include(notYielded)
    }

    "be clean when the else RECORDS its refusal as a different event" in { (td: TestData) =>
      // The idiom that made the first version of this rule wrong. An event-sourced entity often
      // declines by recording a rejection event rather than by `error`/`require`, which is
      // arguably the more faithful design -- the refusal belongs in the event log. Found in
      // riddl-models reactive-bbq (LoyaltyAccount.riddl:579), which this rule flagged despite
      // the model being well formed. Emitting ANY message settles a path; only doing nothing
      // does not.
      errorsFor(
        """when p.flagged then yield event Dom.Ours.Paid
          |            else yield event Dom.Ours.Rejected end""".stripMargin,
        td.name
      ) mustNot include(notYielded)
    }

    "be clean when a statement after the `when` yields on the fall-through" in { (td: TestData) =>
      // `exists` over the sequence is the right combinator: execution passes through every
      // statement, so a trailing unconditional yield settles the obligation whatever the
      // branch above it did.
      errorsFor(
        """when p.flagged then error "refused" end
          |            yield event Dom.Ours.Paid""".stripMargin,
        td.name
      ) mustNot include(notYielded)
    }
  }

  "a `foreach` body" should {

    "not settle the obligation -- it may iterate zero times" in { (td: TestData) =>
      errorsFor(
        """foreach t in field Pay.tags { yield event Dom.Ours.Paid }""",
        td.name
      ) must include(notYielded)
    }
  }
}
