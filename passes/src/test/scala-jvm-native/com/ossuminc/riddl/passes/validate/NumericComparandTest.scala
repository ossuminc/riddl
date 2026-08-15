/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** A28 narrowed comparands to refs so a literal comparison could not be built at all. Reid
  * reversed that 2026-08-14: the corpus held ONE constant across 189 models, so the rule had no
  * uptake to protect. The intent survives as a StyleWarning.
  *
  * **`showStyleWarnings` must be ON here.** The default suppresses exactly the message under test,
  * and a suite that cannot see its own signal reports a confident zero.
  */
class NumericComparandTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    // Cases below assert the ABSENCE of a warning, which a fixture that never parsed satisfies for
    // free. Refuse to report on one.
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end diagnostics

  private def model(condition: String): String =
    // NOTE: the constant's VALUE is still quoted ("5", not 5). `Constant.value` is not yet widened
    // to accept a bare NumericLiteral -- that is Task 4's scope, not this task's -- so a bare `5`
    // here fails to parse ("Expected (\"\\\"\")"). Categorization is unaffected: comparandCategory's
    // ConstantRef arm classifies by the constant's DECLARED type (`Integer`), never by how its
    // value literal is currently spelled.
    s"""domain D is {
       |  context C is {
       |    constant MaxCount: Integer = "5"
       |    record St is { count: Integer, note: String } with { briefly "st" }
       |    command Cmd is { why: String } with { briefly "cmd" }
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on command Cmd { when $condition then do "big" end }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def literalWarnings(msgs: Messages): Messages =
    msgs.justStyle.filter(_.message.contains("named constant"))

  "a numeric literal comparand" should {

    "parse and validate without an Error" in { (td: TestData) =>
      val msgs = diagnostics(model("count > 5"), "literal-comparand")
      withClue(msgs.map(_.message).mkString("\n")) {
        msgs.justErrors mustBe empty
      }
    }

    "draw exactly one StyleWarning suggesting a named constant" in { (td: TestData) =>
      val msgs = diagnostics(model("count > 5"), "literal-style")
      // EXACTLY ONE, not `nonEmpty`: over-firing is the plausible failure here, and `nonEmpty`
      // cannot tell one warning from three.
      withClue(msgs.map(_.message).mkString("\n")) {
        literalWarnings(msgs).size mustBe 1
      }
    }

    "stay silent when the comparison names a constant" in { (td: TestData) =>
      val msgs = diagnostics(model("count > constant MaxCount"), "named-constant")
      withClue(msgs.map(_.message).mkString("\n")) {
        literalWarnings(msgs) mustBe empty
      }
    }

    "accept a decimal and a negative literal" in { (td: TestData) =>
      literalWarnings(diagnostics(model("count > 1.5"), "decimal")).size mustBe 1
      literalWarnings(diagnostics(model("count > -1"), "negative")).size mustBe 1
    }
  }

  "a boolean comparand" should {
    // NOT `diagnostics`/`parseAndValidate`: those route a genuine PARSE failure through
    // `AbstractValidatingTest.parseAndValidateInput`'s `Left` branch, which calls ScalaTest's
    // `fail(...)` directly rather than handing control back to the assertion below -- so the
    // deliberate-parse-error case has to be asserted the way `StatementsTest.parseLetExprFails`
    // does it: call `TopLevelParser.parseInput` directly and match on `Left`.
    "remain a parse error — true/false are atoms, not comparands" in { (td: TestData) =>
      val input = RiddlParserInput(model("count > true"), td)
      TopLevelParser.parseInput(input) match
        case Left(messages) => messages.justErrors must not be empty
        case Right(_)        => fail("expected a PARSE error for 'count > true'")
    }
  }
}
