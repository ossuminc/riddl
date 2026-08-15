/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.pc

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Task 4 (numeric-literals plan): `Constant.value` widened from a bare `LiteralString` to
  * `ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue`, so a
  * `constant` may hold a bare number, a boolean, or a prompt hole -- not only a quoted string.
  *
  * No parser change was needed for the `is`/`:` separator -- `CommonParser.is` already accepted
  * both (and `are`/`=`/omission) -- so cases 1 and 2 below are asserted to parse IDENTICALLY, not
  * merely both successfully.
  */
class ConstantValueTest extends AnyWordSpec with Matchers {

  private def parseConstant(constantSrc: String, origin: String): Constant = {
    val model =
      s"""domain D is {
         |  context C is {
         |    $constantSrc
         |  }
         |}
         |""".stripMargin
    TopLevelParser.parseInput(RiddlParserInput(model, origin)) match
      case Left(messages) => fail(s"parse of $origin failed:\n${messages.format}")
      case Right(root) =>
        val ctx = AST.getContexts(AST.getTopLevelDomains(root).head).head
        ctx.constants.headOption.getOrElse(fail(s"no constant parsed in $origin"))
  }

  "constant" should {

    "hold a bare numeric literal, written with `is`" in {
      val c = parseConstant("constant Max is Integer = 5", "constant-numeric-is")
      c.value match
        case nl: NumericLiteral => nl.text mustBe "5"
        case other              => fail(s"expected a NumericLiteral, got $other")
    }

    // The colon already worked before this task (CommonParser.is), so this must parse to the
    // SAME shape as the `is` case above -- not merely parse.
    "hold a bare numeric literal, written with `:`, identically to `is`" in {
      val c = parseConstant("constant Max: Integer = 5", "constant-numeric-colon")
      c.value match
        case nl: NumericLiteral => nl.text mustBe "5"
        case other              => fail(s"expected a NumericLiteral, got $other")
    }

    "hold a boolean literal" in {
      val c = parseConstant("constant Enabled is Boolean = true", "constant-boolean")
      c.value match
        case bl: BooleanLiteral => bl.value mustBe true
        case other              => fail(s"expected a BooleanLiteral, got $other")
    }

    "hold a prompt value" in {
      val c = parseConstant(
        """constant Gravity is Real = prompt("the gravitational constant")""",
        "constant-prompt"
      )
      c.value match
        case pv: PromptValue => pv.prompt.s mustBe "the gravitational constant"
        case other           => fail(s"expected a PromptValue, got $other")
    }

    "still hold a literal string" in {
      val c = parseConstant("""constant Name is String = "Fred"""", "constant-string")
      c.value match
        case ls: LiteralString => ls.s mustBe "Fred"
        case other             => fail(s"expected a LiteralString, got $other")
    }

    // Scoped narrowly: the deprecation fires only because Natural is a NumericType AND "10"
    // parses as a numeric literal's text. `parseInputWithMessages` is required here -- parse-time
    // messages (deprecations included) are discarded by a plain `parseInput`/`parseAndValidate`.
    //
    // The parser CONSUMES the quoted spelling into a `NumericLiteral` -- the value is no longer a
    // `LiteralString` after this. That is what makes `autoFixable = true` honest: there is no
    // old-shaped node left for prettify to decide about, so re-emitting the AST always produces the
    // bare literal and the round trip converges.
    "parse a quoted numeric literal for a numeric type as a NumericLiteral, and draw a deprecation" in {
      val model =
        """domain D is {
          |  context C is {
          |    constant Max is Natural = "10"
          |  }
          |}
          |""".stripMargin
      TopLevelParser.parseInputWithMessages(
        RiddlParserInput(model, "constant-quoted-numeric")
      ) match
        case Left(messages) => fail(s"parse failed:\n${messages.format}")
        case Right((root, msgs)) =>
          val ctx = AST.getContexts(AST.getTopLevelDomains(root).head).head
          val c = ctx.constants.headOption.getOrElse(fail("no constant parsed"))
          c.value match
            case nl: NumericLiteral => nl.text mustBe "10"
            case other              => fail(s"expected a NumericLiteral, got $other")
          withClue(msgs.format) {
            msgs.exists(m =>
              m.kind == Messages.Deprecation && m.message.contains("numeric literal")
            ) mustBe true
          }
    }
  }
}
