/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** BACKLOG [1.30]: `call`, `create` and `terminate` arguments were type-checked only against an
  * ALIASED parameter type. A parameter declared `DateTime`, `Integer` or `Boolean` took any value of
  * any kind. They are now held to exactly what a constructor argument is: the same literal
  * conformance, assignability and optional-into-required checks, through the one shared helper.
  *
  * Every case asserts the whole model's error count, so a fixture that failed to parse cannot pass
  * vacuously; the control asserts the fixture itself is clean.
  */
class CallCreateArgumentTypesTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** `g` is the handled `Go`: `n: Integer`, `s: String`, `maybeN: Integer?`, `when: DateTime`. */
  private def model(body: String): String =
    s"""domain D is { context C is {
       |  record In is { at: DateTime, n: Integer, ok: Boolean, name: String } with { briefly "i" }
       |  record Out is { r: Integer } with { briefly "o" }
       |  function F is {
       |    requires record In
       |    returns record Out
       |    return record Out(r = 0)
       |  } with { briefly "f" }
       |  command Go is { n: Integer, s: String, maybeN: Integer?, when: DateTime }
       |    with { briefly "c" }
       |  entity Target is {
       |    handler TH is {
       |      on init(seed: Integer, at: DateTime) is { do "start" }
       |      on other is { error "x" }
       |    } with { briefly "th" }
       |  } with { briefly "t" }
       |  entity E is {
       |    inlet I is command Go
       |    handler H is {
       |      on g: command Go is {
       |        $body
       |        do "used"
       |      }
       |      on other is { error "x" }
       |    } with { briefly "h" }
       |  } with { briefly "e" }
       |} }
       |""".stripMargin

  private val goodCall = "let r = call function F(at = g.when, n = g.n, ok = true, name = g.s)"

  private def mustBeClean(body: String, td: TestData): Unit =
    val msgs = messages(model(body), td)
    withClue(clue(msgs)) { errors(msgs) mustBe empty }

  private def mustRefuseOnce(body: String, mentioning: String, td: TestData): Message =
    val msgs = messages(model(body), td)
    withClue(clue(msgs)) {
      errors(msgs).size mustBe 1
      errors(msgs).head.message must include(mentioning)
    }
    errors(msgs).head

  "the fixture (control)" should {
    "be clean with well-typed call and create arguments" in { (td: TestData) =>
      mustBeClean(goodCall + "\n        let t = create entity Target(seed = g.n, at = g.when)", td)
    }
  }

  "a `call` argument for a predefined-type input [1.30]" should {
    "refuse a String literal for an Integer" in { (td: TestData) =>
      mustRefuseOnce("let r = call function F(at = g.when, n = \"five\", ok = true, name = g.s)", "'n'", td)
    }
    "refuse a String reference for an Integer" in { (td: TestData) =>
      mustRefuseOnce("let r = call function F(at = g.when, n = g.s, ok = true, name = g.s)", "'n'", td)
    }
    "refuse a number for a Boolean" in { (td: TestData) =>
      mustRefuseOnce("let r = call function F(at = g.when, n = g.n, ok = 3, name = g.s)", "Boolean", td)
    }
    "refuse a non-ISO string for a DateTime" in { (td: TestData) =>
      mustRefuseOnce(
        "let r = call function F(at = \"tomorrow\", n = g.n, ok = true, name = g.s)",
        "tomorrow",
        td
      )
    }
    "advise, not refuse, an optional value for a required input" in { (td: TestData) =>
      val msgs = messages(
        model("let r = call function F(at = g.when, n = g.maybeN, ok = true, name = g.s)"),
        td
      )
      withClue(clue(msgs)) {
        errors(msgs) mustBe empty
        msgs.count(_.ruleId.contains(RuleId.OptionalForRequired)) mustBe 1
      }
    }
  }

  "a `create` argument for a predefined-type `on init` parameter [1.30]" should {
    "refuse a String reference for an Integer" in { (td: TestData) =>
      mustRefuseOnce("let t = create entity Target(seed = g.s, at = g.when)", "'seed'", td)
    }
    "refuse a number for a DateTime" in { (td: TestData) =>
      mustRefuseOnce("let t = create entity Target(seed = g.n, at = 5)", "at", td)
    }
  }
}
