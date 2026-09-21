/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** B6 and B8 from riddl-generator's 2026-09-17 list (landed 2026-09-21): an ADVISORY on a
  * `prompt(...)` used as a yield argument, and a STYLE warning on `do` prose that validates a
  * field against a range or set a TYPE could express.
  */
class YieldPromptAndRangeLintTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def of(msgs: Messages, rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  private def model(body: String): String =
    s"""domain D is {
       |  context C is {
       |    command Book yields event Booked is { id: String, when: TimeStamp, size: Integer }
       |      with { briefly "c" }
       |    event Booked is { id: String, at: TimeStamp, size: Integer } with { briefly "e" }
       |    entity E is {
       |      record Fields is { id: String, at: TimeStamp } with { briefly "f" }
       |      state Main of record E.Fields
       |      inlet In is command Book with { briefly "i" }
       |      outlet Out is event Booked with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Booked(id = "x", at = system.now, size = 1) }
       |        on b: command Book is {
       |          $body
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a prompt as a yield argument (B6)" should {

    "be an Advisory naming the argument" in { (td: TestData) =>
      val found = of(
        diagnostics(model("""yield event Booked(id = b.id, at = prompt("the previous time"), size = b.size)"""), td.name),
        RuleId.YieldArgumentPrompt
      )
      found.size mustBe 1
      found.head.kind mustBe Advisory
      found.head.message must include("'at'")
    }

    "fire per argument, naming a positional one by number" in { (td: TestData) =>
      val found = of(
        diagnostics(model("""yield event Booked(prompt("id"), at = prompt("when"), size = b.size)"""), td.name),
        RuleId.YieldArgumentPrompt
      )
      found.size mustBe 2
      found.map(_.message).exists(_.contains("#1")) mustBe true
    }

    "NOT fire on stated values, including an expression" in { (td: TestData) =>
      of(diagnostics(model("yield event Booked(id = b.id, at = b.when + 1 hour, size = b.size)"), td.name),
        RuleId.YieldArgumentPrompt) mustBe empty
    }

    "NOT fire on a tell -- yield only" in { (td: TestData) =>
      val src = model(
        """yield event Booked(id = b.id, at = b.when, size = b.size)
          |          tell event Booked(id = b.id, at = prompt("x"), size = b.size) to entity C.E""".stripMargin
      )
      of(diagnostics(src, td.name), RuleId.YieldArgumentPrompt) mustBe empty
    }
  }

  "prose that validates a range (B8)" should {

    def withYield(stmt: String): String =
      model(s"""$stmt
               |          yield event Booked(id = b.id, at = b.when, size = b.size)""".stripMargin)

    "be a Style warning naming the field" in { (td: TestData) =>
      val found = of(
        diagnostics(withYield("""do "validate MakeReservation.partySize is between 1 and 20""""), td.name),
        RuleId.DoValidatesARange
      )
      found.size mustBe 1
      found.head.kind mustBe StyleWarning
      found.head.message must include("'MakeReservation.partySize'")
    }

    "accept the other verbs and bounds" in { (td: TestData) =>
      of(diagnostics(withYield("""do "ensure that qty must be at least 1""""), td.name),
        RuleId.DoValidatesARange).size mustBe 1
      of(diagnostics(withYield("""do "Check b.size is one of the allowed sizes""""), td.name),
        RuleId.DoValidatesARange).size mustBe 1
    }

    "NOT fire on prose that merely mentions a bound, or on a prompt VALUE" in { (td: TestData) =>
      of(diagnostics(withYield("""do "validate the address""""), td.name), RuleId.DoValidatesARange) mustBe empty
      of(diagnostics(withYield("""do "reordered stops needing the distances between every pair""""), td.name),
        RuleId.DoValidatesARange) mustBe empty
      of(diagnostics(withYield("""when prompt("b.size is greater than zero") then { do "x" } end"""), td.name),
        RuleId.DoValidatesARange) mustBe empty
    }

    "reach a `do` nested inside a `when` body" in { (td: TestData) =>
      of(diagnostics(withYield("""when b.size > 0 then { do "validate b.size is between 1 and 20" } end"""), td.name),
        RuleId.DoValidatesARange).size mustBe 1
    }
  }
}
