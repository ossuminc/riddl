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

/** An `external context` describes a system this model deliberately does not implement, so
  * completeness checks must not report it as unfinished.
  *
  * There are TWO spellings and they are not interchangeable: `external context Foo` sets
  * `Context.intention`, while `context Foo is { … } with { option external }` sets an option.
  * `hasOption("external")` sees only the second, and models write the first almost exclusively.
  * Three separate sites asked only `hasOption`; one of them produced 1120 false warnings across
  * riddl-models in a single run before a corpus A/B caught it (2026-08-12). All four now route
  * through `StreamingValidation.isExternalContext`, which asks both.
  *
  * **Every case here has a control** — the same model with the `external` marker removed, asserting
  * the warning DOES fire. Without them a broken exemption that silenced everything would pass, and
  * so would an exemption that was never reached.
  */
class ExternalContextExemptionTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def textFor(src: String, origin: String): String =
    diagnostics(src, origin).map(_.message).mkString("\n")

  /** A handler whose only statement is a `do`, inside a context that is external or not. */
  private def doOnlyHandler(external: Boolean): String =
    s"""domain D is {
       |  ${if external then "external context" else "context"} C is {
       |    command Poke is { why: String } with { briefly "c" }
       |    entity E is {
       |      handler H is { on command Poke is { do "think about it" } } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** An entity handling a command without emitting an event — the A19 completeness check. */
  private def commandWithoutEvent(external: Boolean): String =
    s"""domain D is {
       |  ${if external then "external context" else "context"} C is {
       |    command Poke is { why: String } with { briefly "c" }
       |    record R is { v: Integer } with { briefly "r" }
       |    entity E is {
       |      state S of record R is {
       |        handler H is { on command Poke is { set field R.v to "1" } } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an external context" should {

    "be exempt from the 'only do statements' warning" in { (td: TestData) =>
      textFor(doOnlyHandler(external = true), "external-do-only") must not(
        include("contains only 'do' statements")
      )
    }

    "CONTROL: a non-external context is NOT exempt" in { (td: TestData) =>
      // Without this the case above would pass even if the warning had been deleted outright.
      textFor(doOnlyHandler(external = false), "plain-do-only") must include(
        "contains only 'do' statements"
      )
    }

    "be exempt from the command-should-yield-an-event check" in { (td: TestData) =>
      textFor(commandWithoutEvent(external = true), "external-no-event") must not(
        include("should result in sending an event")
      )
    }

    "CONTROL: a non-external context is NOT exempt from it either" in { (td: TestData) =>
      textFor(commandWithoutEvent(external = false), "plain-no-event") must include(
        "should result in sending an event"
      )
    }

    "be exempt when marked with the legacy option rather than the intention" in { (td: TestData) =>
      // The other spelling must keep working: it is what `hasOption` was written for, and dropping
      // it while "fixing" the intention would trade one blind spot for the other.
      val src = doOnlyHandler(external = false).replace(
        """  } with { briefly "c" }
          |} with { briefly "d" }""".stripMargin,
        """  } with { briefly "c" option external }
          |} with { briefly "d" }""".stripMargin
      )
      textFor(src, "legacy-option-external") must not(include("contains only 'do' statements"))
    }
  }
}
