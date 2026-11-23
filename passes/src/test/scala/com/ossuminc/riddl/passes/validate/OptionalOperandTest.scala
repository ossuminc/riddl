/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** An OPTIONAL operand of an ordering comparison or of arithmetic (Reid, 2026-11-23, synapify's
  * DineIn run at reactive-bbq `InventoryItem.riddl:555`): the model gives the expression no value
  * when the operand is absent, so it is the `value-optional-operand` Advisory unless the model says
  * what happens -- `otherwise`, a guard, or a short-circuiting `and`/`or`.
  */
class OptionalOperandTest extends AbstractValidatingTest {

  /** `body` runs in a clause of `E` with state `E.S` of record `E.F`, where `opt` is `Integer?`, `n`
    * is `Integer` and `items` is `Integer*`, and the handled command `g` carries `m: Integer`.
    */
  private def model(body: String): String =
    s"""domain D is { context C is {
       |  command Go is { m: Integer } with { briefly "c" }
       |  entity E is {
       |    record Line is { k: Integer, cost: Integer, maybe: Integer? } with { briefly "l" }
       |    record F is { opt: Integer?, n: Integer, items: Integer*, lines: E.Line* }
       |      with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    handler H is {
       |      on g: command Go is {
       |        $body
       |      }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  private def all(body: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(model(body), origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured

  private def advisories(body: String, origin: String): Messages =
    val ms = all(body, origin)
    withClue(ms.map(_.format).mkString("\n")) { ms.filter(_.kind == Messages.Error) mustBe empty }
    ms.filter(_.ruleId.contains(RuleId.OptionalOperand))

  private def set(cond: String): String =
    s"when $cond then set field E.S.n to 1 end"

  "an optional operand" should {
    "be advised in an ordering comparison (reactive-bbq's shape)" in { (td: TestData) =>
      val as = advisories(set("g.m <= E.S.opt"), td.name)
      as must have size 1
      as.head.kind mustBe Messages.Advisory
      as.head.message must include("E.S.opt")
    }

    "be advised in arithmetic, on either side" in { (td: TestData) =>
      advisories("set field E.S.n to E.S.opt + 1", td.name) must have size 1
      advisories("set field E.S.n to 2 * E.S.opt", td.name) must have size 1
    }

    "be advised once on the leaf, not again on the expression it is in" in { (td: TestData) =>
      val as = advisories(set("E.S.opt + 1 <= g.m"), td.name)
      as must have size 1
      as.head.message must include("'+'")
    }

    "still be advised when the guard comes AFTER the use" in { (td: TestData) =>
      advisories(set("g.m <= E.S.opt or E.S.opt != empty"), td.name) must have size 1
    }
  }

  "an optional operand the model accounts for" should {
    "be silent under otherwise" in { (td: TestData) =>
      advisories(set("g.m <= (E.S.opt otherwise 0)"), td.name) mustBe empty
    }
    "be silent inside a when guard" in { (td: TestData) =>
      advisories(
        "when E.S.opt != empty then when g.m <= E.S.opt then set field E.S.n to 1 end end",
        td.name
      ) mustBe empty
    }
    "be silent after a require" in { (td: TestData) =>
      advisories("require E.S.opt != empty\n        " + set("g.m <= E.S.opt"), td.name) mustBe empty
    }
    "be silent behind a short-circuiting and" in { (td: TestData) =>
      advisories(set("E.S.opt != empty and g.m <= E.S.opt"), td.name) mustBe empty
    }
    "be silent behind a short-circuiting or" in { (td: TestData) =>
      advisories(set("E.S.opt == empty or g.m <= E.S.opt"), td.name) mustBe empty
    }
  }

  "a path THROUGH a guarded optional (BACKLOG [1.34])" should {
    val pick = "let line = one of E.S.lines as l where l.k == g.m\n        "
    "be silent when the guard proves the prefix and the rest is required" in { (td: TestData) =>
      val ms = all(
        pick + """when line == empty then error "none" else set field E.S.n to line.cost * 2 end""",
        td.name
      )
      withClue(ms.map(_.format).mkString("\n")) {
        ms.filter(_.ruleId.contains(RuleId.OptionalOperand)) mustBe empty
      }
    }
    // A constructor argument, as at shopping-cart Cart.riddl:892 -- `set field` does not run the
    // optional-for-required check, so a `set` here would pass with or without the fix.
    "be silent for optional-for-required as well" in { (td: TestData) =>
      val ms = all(
        pick + """when line == empty then error "none" else log record E.Line(k = line.cost, cost = 1, maybe = empty) end""",
        td.name
      )
      withClue(ms.map(_.format).mkString("\n")) {
        ms.filter(_.ruleId.contains(RuleId.OptionalForRequired)) mustBe empty
      }
    }
    "be silent when a LONGER guarded path proves the prefix present" in { (td: TestData) =>
      // `line.maybe` non-empty implies `line` present (shopping-cart's guard on `line.quantity`).
      advisories(
        pick + """when line.maybe == empty then error "none" else set field E.S.n to line.cost * 2 end""",
        td.name
      ) mustBe empty
    }
    "still be advised when a segment AFTER the guarded prefix is optional" in { (td: TestData) =>
      advisories(
        pick + """when line == empty then error "none" else set field E.S.n to line.maybe * 2 end""",
        td.name
      ) must have size 1
    }
    "still be advised without the guard" in { (td: TestData) =>
      advisories(pick + "set field E.S.n to line.cost * 2", td.name) must have size 1
    }
  }

  "equality and non-optional operands" should {
    "not be advised: equality is the presence test" in { (td: TestData) =>
      advisories(set("E.S.opt == g.m"), td.name) mustBe empty
      advisories(set("E.S.opt != empty"), td.name) mustBe empty
    }
    "not be advised for a required operand" in { (td: TestData) =>
      advisories(set("g.m <= E.S.n"), td.name) mustBe empty
    }
  }
}
