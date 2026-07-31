/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Options on portlets must be validated like options anywhere else.
  *
  * They were not: `Inlet`/`Outlet` are `Leaf`s rather than `VitalDefinition`s, so
  * `checkDefinition` fell straight past the metadata branch and nothing ever looked at their
  * options. A typo'd or invented option on an outlet was accepted in silence while the same typo
  * on any vital definition drew a StyleWarning. Reported by riddl-generator.
  */
class PortletOptionTest extends AbstractValidatingTest {

  private def model(outletOption: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Thing is { id: Integer } with { briefly "a thing" }
       |    processor F as flow is {
       |      inlet In is command Dom.Ctx.Thing
       |      outlet Out is command Dom.Ctx.Thing with { $outletOption }
       |    } with { briefly "a flow" }
       |  } with { briefly "a context" }
       |} with { briefly "a domain" }
       |""".stripMargin

  "an unrecognized option on an outlet" should {
    "draw a StyleWarning, as it does on a vital definition" in { (td: TestData) =>
      parseAndValidateDomain(RiddlParserInput(model("""option zzznotanoption("x")"""), td), shouldFailOnErrors = false) { case (_, _, messages) =>
        val unrecognized = messages.filter(_.message.contains("not a recognized RIDDL option"))
        withClue(s"messages were: ${messages.format}") {
          unrecognized must not be empty
        }
      }
    }
  }

  "the `lowering` option" should {
    "be accepted on an outlet without a StyleWarning" in { (td: TestData) =>
      // riddl-generator's documented placement: it reads the option off the outlet first.
      parseAndValidateDomain(RiddlParserInput(model("""option lowering("emitter")"""), td), shouldFailOnErrors = false) { case (_, _, messages) =>
        val unrecognized = messages.filter(_.message.contains("not a recognized RIDDL option"))
        withClue(s"messages were: ${messages.format}") {
          unrecognized mustBe empty
        }
      }
    }

    "be accepted on the processor, the form a modeler reaches for to set it once" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Thing is { id: Integer } with { briefly "a thing" }
          |    processor F as flow is {
          |      inlet In is command Dom.Ctx.Thing
          |      outlet Out is command Dom.Ctx.Thing
          |    } with { briefly "a flow" option lowering("outgoing") }
          |  } with { briefly "a context" }
          |} with { briefly "a domain" }
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) { case (_, _, messages) =>
        val unrecognized = messages.filter(_.message.contains("not a recognized RIDDL option"))
        withClue(s"messages were: ${messages.format}") {
          unrecognized mustBe empty
        }
      }
    }

    "still complain about wrong arity, as `technology` does" in { (td: TestData) =>
      // Registering the name must not make it a free pass: the spec says exactly one argument.
      parseAndValidateDomain(RiddlParserInput(model("option lowering"), td), shouldFailOnErrors = false) { case (_, _, messages) =>
        val arity = messages.filter { m =>
          m.message.contains("lowering") && !m.message.contains("not a recognized RIDDL option")
        }
        withClue(s"messages were: ${messages.format}") {
          arity must not be empty
        }
      }
    }
  }

  "a portlet with no metadata at all" should {
    "NOT draw a 'metadata should not be empty' warning" in { (td: TestData) =>
      // Validating portlet metadata CONTENTS must not import the expectation that portlets
      // carry metadata; ordinary inlets and outlets have none and that is correct.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Thing is { id: Integer } with { briefly "a thing" }
          |    processor F as flow is {
          |      inlet In is command Dom.Ctx.Thing
          |      outlet Out is command Dom.Ctx.Thing
          |    } with { briefly "a flow" }
          |  } with { briefly "a context" }
          |} with { briefly "a domain" }
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) { case (_, _, messages) =>
        val portletEmpty = messages.filter { m =>
          m.message.contains("should not be empty") && m.message.contains("Out")
        }
        withClue(s"messages were: ${messages.format}") {
          portletEmpty mustBe empty
        }
      }
    }
  }
}
