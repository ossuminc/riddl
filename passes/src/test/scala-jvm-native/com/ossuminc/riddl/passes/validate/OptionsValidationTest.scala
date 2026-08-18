/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

/** Unit Tests for Options Validation */
class OptionsValidationTest extends AbstractValidatingTest {

  "Options" should {
    "identify incorrect css" in { (td: TestData) =>

      val input: String =
        """domain ignore {
          |  context invalid {
          |    type JustHereToConformToSyntax = String
          |  } with {
          |    option css("fill:#333", "color:white")
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, "identify incorrect css test case") {
        (_: Root, _: RiddlParserInput, messages: Messages) =>
          if messages.justErrors.nonEmpty then fail(messages.justErrors.format)
          else succeed
          end if
      }
    }
  }

  /** `persistent` states DOMAIN DURABILITY, so it belongs only where there is state to persist.
    *
    * Reid's ruling, 2026-08-07: a Connector is the only definition that takes it as an OPTION. An
    * Entity says persistence with an intention keyword; a Repository is persistent by implication.
    * Everything else -- a Context above all -- has no state of its own to persist, so writing it
    * there is a semantic blunder rather than a weaker-but-legitimate choice, and a StyleWarning
    * (what it drew before) understates it.
    */
  "the `persistent` option" should {

    // `shouldFailOnErrors = false` because these cases are ABOUT producing an error: the default
    // aborts the helper before the assertion can look at it.
    def validateFor(body: String, label: String)(check: Messages => Unit): Unit =
      parseAndValidate(body, label, shouldFailOnErrors = false) {
        (_: Root, _: RiddlParserInput, messages: Messages) =>
          check(messages)
          succeed
      }

    "be an ERROR on a context, whose state lives in its contents" in { (_: TestData) =>
      val input =
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option persistent }
          |}
          |""".stripMargin
      validateFor(input, "persistent on context") { messages =>
        val errs = messages.justErrors.filter(_.message.contains("persistent"))
        withClue(s"expected an ERROR for persistent on a Context, got:\n${messages.format}") {
          errs mustNot be(empty)
        }
      }
    }

    "be an ERROR on a gateway context specifically" in { (_: TestData) =>
      // The case riddl-generator filed: a gateway is a pass-through and initiates transactions
      // rather than holding domain state. The merge shape is supplied so the intention's own
      // shape rule (ValidationPass:3030) is not what fails.
      val input =
        """domain D is {
          |  type T is String
          |  gateway context G is {
          |    inlet in1 is type D.T
          |    inlet in2 is type D.T
          |    outlet out1 is type D.T
          |  } with { option persistent }
          |}
          |""".stripMargin
      validateFor(input, "persistent on gateway context") { messages =>
        val errs = messages.justErrors.filter(_.message.contains("persistent"))
        withClue(
          s"expected an ERROR for persistent on a gateway Context, got:\n${messages.format}"
        ) {
          errs mustNot be(empty)
        }
      }
    }

    "stay clean on a Connector, the one definition that takes it" in { (_: TestData) =>
      // The other half of the contract, and the one that matters most: all 426 uses of this
      // option across riddl-models are on connectors, so a rule that caught them would be a
      // corpus-wide break rather than a fix.
      val input =
        """domain D is {
          |  type Pkg is { id: String }
          |  context W is { source picked is { outlet out is type D.Pkg } }
          |  context K is { sink loaded is { inlet in is type D.Pkg } }
          |  connector handoff is {
          |    from outlet D.W.picked.out to inlet D.K.loaded.in
          |  } with { option persistent }
          |}
          |""".stripMargin
      validateFor(input, "persistent on connector") { messages =>
        val about = messages.filter(_.message.contains("persistent"))
        withClue(s"expected NOTHING about persistent on a Connector, got:\n${messages.format}") {
          about mustBe empty
        }
      }
    }

    "leave other misplaced options at StyleWarning" in { (_: TestData) =>
      // The severity is per-option on purpose. Promoting EVERY validParents violation to an Error
      // was not ruled on and would be a corpus-wide behaviour change.
      val input =
        """domain D is {
          |  context C is {
          |    type T is String
          |  } with { option auto-id }
          |}
          |""".stripMargin
      validateFor(input, "auto-id on context") { messages =>
        withClue(s"auto-id misplaced should NOT be an error, got:\n${messages.format}") {
          messages.justErrors.filter(_.message.contains("auto-id")) mustBe empty
        }
        withClue(s"auto-id misplaced should still be reported, got:\n${messages.format}") {
          messages.filter(_.message.contains("auto-id")) mustNot be(empty)
        }
      }
    }
  }
}
