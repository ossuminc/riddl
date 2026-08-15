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

/** Message-value-source plan, Task 5 (Reid, 2026-08-14: *"no further task is needed, just build
  * it"*): `let x = initiate entity Order(…)` whose `x` is never referenced again.
  *
  * `initiate` is the ONLY way an `Id(P)` value comes into being, so dropping the one it produces
  * usually means the author meant to address the new instance and forgot.
  *
  * **It is a plain Warning, and the self-terminating-worker case below is why.** A fire-and-forget
  * instance legitimately has an unused id, and since `initiate` is a VALUE there is no
  * argument-less spelling to steer such an author toward — the `let` IS how you write it. That case
  * is a REQUIRED member of this suite: without it, promoting the rule to an Error would still look
  * green.
  *
  * The "used" cases are the load-bearing half in the other direction. The check decides usage from
  * the RENDERED body rather than by enumerating escape routes, precisely so a route cannot be
  * missed; the nested-`when` case pins that a use inside a nested body counts, which is where an
  * enumeration would most plausibly have gone wrong.
  */
class UnusedInitiateIdTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def unusedWarnings(msgs: Messages): Seq[String] =
    msgs.map(_.message).filter(_.contains("holds the identity of the instance"))

  private def model(callerBody: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    command Note is { wid: Id(entity Worker) } with { briefly "n" }
       |    record R is { total: String } with { briefly "r" }
       |    entity Worker is {
       |      state WS of record R is {
       |        handler WH is {
       |          on init(total: String) is { do "start" }
       |          on term is { do "end" }
       |          on command Note is { do "note" }
       |        } with { briefly "wh" }
       |      } with { briefly "ws" }
       |    } with { briefly "we" }
       |    entity Caller is {
       |      record CR is { held: Id(entity Worker) } with { briefly "cr" }
       |      state CS of record Caller.CR is {
       |        handler CH is { on command Go is { $callerBody } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an unreferenced `initiate` id" should {

    "draw a warning" in { (td: TestData) =>
      val msgs = diagnostics(model("""let wid = initiate entity Worker("1")"""), "unused")
      unusedWarnings(msgs).size mustBe 1
    }

    /** The reason this is a Warning and not an Error. A worker that ends itself is never addressed
      * by its creator, so its id is legitimately dropped — the diagnostic is expected here, and
      * must remain something an author can live with.
      */
    "stay a Warning, never an Error, for a self-terminating worker" in { (td: TestData) =>
      val msgs = diagnostics(model("""let wid = initiate entity Worker("1")"""), "self-term")
      unusedWarnings(msgs).size mustBe 1
      msgs.justErrors mustBe empty
    }
  }

  "a referenced `initiate` id" should {

    "stay silent when it is terminated" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""let wid = initiate entity Worker("1")
                |            terminate wid""".stripMargin),
        "used-terminate"
      )
      unusedWarnings(msgs) mustBe empty
    }

    "stay silent when it is kept in state" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""let wid = initiate entity Worker("1")
                |            set field Caller.CR.held to wid""".stripMargin),
        "used-set"
      )
      unusedWarnings(msgs) mustBe empty
    }

    "stay silent when it is passed in a message" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""let wid = initiate entity Worker("1")
                |            tell command Note(wid = wid) to entity Worker""".stripMargin),
        "used-message"
      )
      unusedWarnings(msgs) mustBe empty
    }

    /** A use nested inside a `when` body counts. This is the case an enumerate-the-routes
      * implementation would most plausibly have missed, and the reason the check reads the rendered
      * body — a nesting statement's `format` renders its whole block.
      */
    /** Rendering the body is what made a four-armed match over a five-member union reachable:
      * `WhenStatement.format` had no `PromptValue` arm and threw a `MatchError` on `when
      * prompt("…")`. Nothing had noticed because `PrettifyVisitor` keeps its OWN copy of that
      * dispatch, and that copy is complete — so the reflectivity round trip, the thing that
      * normally proves `format` total, could not reach the hole. This case is the gate.
      */
    "survive a clause that also holds a `when prompt(…)` condition" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""let wid = initiate entity Worker("1")
                |            when prompt("the worker is stuck") then
                |              terminate wid
                |            end""".stripMargin),
        "when-prompt"
      )
      // Asserted FIRST and deliberately: `unusedWarnings mustBe empty` is also satisfied by a
      // model that never parsed, so without this the case could not fail for the reason it exists.
      msgs.justErrors mustBe empty
      unusedWarnings(msgs) mustBe empty
    }

    "stay silent when the only use is nested inside a `when` body" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""let wid = initiate entity Worker("1")
                |            when "the worker is stuck" then
                |              terminate wid
                |            end""".stripMargin),
        "used-nested"
      )
      unusedWarnings(msgs) mustBe empty
    }
  }
}
