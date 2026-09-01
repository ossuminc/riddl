/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{At, Messages, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, Riddl}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** Tests for:
  *   - Part B: the deprecated shape keywords (source/sink/flow/merge/split/router) emit a
  *     [[Messages.Deprecation]] telling the user to use `processor <id> as <kw>`.
  *   - Part A: parse-time messages (warnings/deprecations) accumulated during a *successful* parse
  *     now surface in `Riddl.parseAndValidate`'s output.
  */
class ProcessorDeprecationTest extends AbstractValidatingTest {

  private def flowModel(header: String): String =
    s"""domain D is {
       |  context C is {
       |    command Cmd = { x: Integer }
       |    $header is {
       |      inlet i is command Cmd
       |      outlet o is command Cmd
       |    }
       |  }
       |}
       |""".stripMargin

  // A30: `send` canonically targets an outlet; `send ... to inlet` is deprecated. The `$target`
  // is either `inlet C.P.i` or `outlet C.P.o`, both resolvable ports on the processor `P`.
  private def sendModel(target: String): String =
    s"""domain D is {
       |  context C is {
       |    command Cmd = { x: Integer }
       |    processor P as flow is {
       |      inlet i is command Cmd
       |      outlet o is command Cmd
       |      handler H is {
       |        on command Cmd { send command Cmd(x = "the x") to $target }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "ProcessorDeprecation" must {
    "emit a Deprecation for the `flow` shape keyword" in { (td: TestData) =>
      val rpi = RiddlParserInput(flowModel("flow F"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations
          info(deprecations.format)
          val found = deprecations.exists { (m: Messages.Message) =>
            // `streamlet`, not `processor`: the shape keywords now point at the canonical
            // generic spelling. Pointing an author at one deprecated keyword from another
            // would be worse than saying nothing.
            m.message.contains("flow") && m.message.contains("streamlet")
          }
          found must be(true)
      }
    }

    // INVERTED at 2.0. `reply` was a deprecated synonym for `yield`; it is now the REQUIRED
    // statement for a query's result, so drawing a deprecation would be actively wrong. The case
    // is kept, pointing the other way, so the un-deprecation cannot silently regress.
    "emit NO deprecation for the `reply` statement (2.0: `reply` answers a query)" in {
      (td: TestData) =>
        val rpi = RiddlParserInput(
          """domain D is {
          |  context C is {
          |    result Res is { ok: Boolean }
          |    query Ask replies result Res is { q: Integer }
          |    handler H is {
          |      on query Ask { reply result Res }
          |    }
          |  }
          |}
          |""".stripMargin,
          td
        )
        Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
          case Left(errors) => fail(errors.format)
          case Right(result) =>
            val deprecations = result.messages.justDeprecations
            info(deprecations.format)
            deprecations.exists { (m: Messages.Message) =>
              m.message.contains("`reply` statement is deprecated")
            } must be(false)
        }
    }

    "emit a Deprecation for the `prompt` statement (A54: `do` is canonical)" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    command Cmd is { f: Boolean }
          |    handler H is {
          |      on command Cmd { prompt "do the thing" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations
          info(deprecations.format)
          deprecations.exists { (m: Messages.Message) =>
            m.message.contains("`prompt` statement is deprecated") &&
            m.message.contains("do")
          } must be(true)
      }
    }

    "emit a Deprecation for `send ... to inlet` (A30: outlet is canonical)" in { (td: TestData) =>
      val rpi = RiddlParserInput(sendModel("inlet C.P.i"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val sendDeprecations = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("send to an inlet is deprecated")
          }
          info(result.messages.format)
          sendDeprecations.size must be(1)
          sendDeprecations.head.message must be(
            "send to an inlet is deprecated and will be removed in 3.0; send to your outlet and " +
              "connect it with a connector, or use `tell` to deliver directly to a processor"
          )
      }
    }

    "not emit a Deprecation for `send ... to outlet` (A30)" in { (td: TestData) =>
      val rpi = RiddlParserInput(sendModel("outlet C.P.o"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val sendDeprecations = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("send to an inlet is deprecated")
          }
          sendDeprecations mustBe empty
      }
    }

    "not emit a Deprecation for the canonical `do` statement (A54)" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
        |  context C is {
        |    command Cmd is { f: Boolean }
        |    handler H is {
        |      on command Cmd { do "the thing" }
        |    }
        |  }
        |}
        |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("`prompt` statement is deprecated")
          }
          deprecations mustBe empty
      }
    }

    "not emit a Deprecation for the `streamlet F as flow` form" in { (td: TestData) =>
      val rpi = RiddlParserInput(flowModel("streamlet F as flow"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("keyword is deprecated")
          }
          info(result.messages.format)
          deprecations mustBe empty
      }
    }

    // This case USED to assert the opposite -- that `processor F as flow` was the clean
    // destination the shape keywords pointed at. It was, until [5.1] made `streamlet` canonical.
    // Kept pointing the other way, for the same reason the `reply` case above is: an
    // un-deprecation and a re-deprecation should both be visible, not silently absorbed.
    "emit a Deprecation for the now-deprecated `processor F as flow` form" in { (td: TestData) =>
      val rpi = RiddlParserInput(flowModel("processor F as flow"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("`processor` keyword is deprecated")
          }
          deprecations must not be empty
          // The ascribed shape is untouched -- only the keyword before the identifier moves.
          deprecations.head.message must include("streamlet")
      }
    }

    "surface a parse-time message accumulated during a successful parse" in { (td: TestData) =>
      // The `flow` deprecation is emitted during parsing (not by a validation pass), so its
      // presence in the final result proves parse-time messages now reach the output. Confirm it
      // originates from parsing by checking TopLevelParser.parseInputWithMessages directly.
      val rpi = RiddlParserInput(flowModel("flow F"), td)
      TopLevelParser.parseInputWithMessages(rpi) match {
        case Left(errors) => fail(errors.format)
        case Right((_, parseMessages)) =>
          parseMessages.exists(_.isDeprecation) must be(true)
      }
    }

    "thread PassInput.parseMessages into PassesResult.messages" in { (td: TestData) =>
      val rpi = RiddlParserInput(flowModel("processor F as flow"), td)
      TopLevelParser.parseInput(rpi) match {
        case Left(errors) => fail(errors.format)
        case Right(root) =>
          val parseWarning = Messages.Message(At.empty, "synthetic parse warning", Messages.Warning)
          val input = PassInput(root, List(parseWarning))
          val result = Pass.runThesePasses(input, Pass.standardPasses)
          result.messages.contains(parseWarning) must be(true)
      }
    }
  }
}
