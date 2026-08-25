/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, Finder, Messages}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `when prompt("…")` — a condition an AI evaluates.
  *
  * A54 settled that a bare `"x"` is a LITERAL while `prompt("x")` marks a value an AI decides. A
  * `when` condition written as a bare string is plainly the latter wearing the former's clothes,
  * and `prompt(...)` did not parse in a condition at all. It does now, and the bare-string form is
  * deprecated — still accepted, so no model breaks today.
  */
class WhenPromptRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def model(condition: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { id: String }
       |    entity E is {
       |      handler H is {
       |        on command Dom.Ctx.Go is {
       |          when $condition then
       |            do "something"
       |          end
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def conditionOf(root: Root) =
    Finder(root).recursiveFindByType[WhenStatement].headOption.getOrElse(fail("no when")).condition

  "a prompt condition" should {

    "parse as a PromptValue" in { (_: TestData) =>
      conditionOf(parse(model("""prompt("the order has drink items")"""), "p")) match
        case pv: PromptValue => pv.text mustBe "the order has drink items"
        case other           => fail(s"expected a PromptValue, got $other")
    }

    "survive a prettify round trip as a PromptValue" in { (_: TestData) =>
      val pretty = prettify(parse(model("""prompt("the order has drink items")"""), "p"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("""prompt("the order has drink items")""")
        conditionOf(parse(pretty, "regen")) mustBe a[PromptValue]
      }
    }

    "draw no deprecation" in { (td: TestData) =>
      // Riddl.parseAndValidate, not parseAndValidateInput: parse-time messages reach the result
      // through PassInput.parseMessages, and only this entry point carries them.
      Riddl.parseAndValidate(
        RiddlParserInput(model("""prompt("the order has drink items")"""), td),
        shouldFailOnError = false
      ) match
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages.justDeprecations.filter(
            _.message.contains("bare string `when` condition")
          ) mustBe empty
    }
  }

  "a bare string condition" should {

    "still parse, for compatibility" in { (_: TestData) =>
      conditionOf(parse(model("\"the order has drink items\""), "s")) mustBe a[LiteralString]
    }

    "draw a deprecation" in { (td: TestData) =>
      Riddl.parseAndValidate(
        RiddlParserInput(model("\"the order has drink items\""), td),
        shouldFailOnError = false
      ) match
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations
          withClue(s"deprecations were:\n${deprecations.format}\n") {
            deprecations.exists(_.message.contains("bare string `when` condition")) mustBe true
          }
    }
  }
}
