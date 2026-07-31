/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Options on portlets must be validated like options anywhere else.
  *
  * They were not: `Inlet`/`Outlet` are `Leaf`s rather than `VitalDefinition`s, so `checkDefinition`
  * fell straight past the metadata branch and nothing ever looked at their options. A typo'd or
  * invented option on an outlet was accepted in silence while the same typo on any vital definition
  * drew a StyleWarning. Reported by riddl-generator.
  */
class PortletOptionTest extends AbstractValidatingTest {

  /** Parse and validate `src`, returning its messages with style warnings explicitly ON.
    *
    * Two things this guards, both learned the hard way:
    *
    *   1. The unrecognized-option message is a StyleWarning, and `Messages.Accumulator` DROPS those
    *      unless `showStyleWarnings` is set. `pc.options` is GLOBAL mutable state that other suites
    *      change via `withOptions`, so without pinning it here the case passed in isolation and
    *      failed inside the full suite, depending on which suite last touched the flag. 2. The
    *      model must VALIDATE cleanly apart from the option under test. An earlier fixture declared
    *      a flow with no inlet; the arity errors then dominated the message set and the option
    *      warning never appeared.
    */
  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  /** A legal flow — one inlet, one outlet — carrying `outletOption` on the outlet. */
  private def model(outletOption: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Thing is { id: Integer } with { briefly "a thing" }
       |    processor Proc as flow is {
       |      inlet In is command Dom.Ctx.Thing
       |      outlet Out is command Dom.Ctx.Thing with { $outletOption }
       |    } with { briefly "a flow" }
       |  } with { briefly "a context" }
       |} with { briefly "a domain" }
       |""".stripMargin

  /** The same legal flow, with the option on the PROCESSOR instead of the outlet. */
  private def processorModel(processorOption: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Thing is { id: Integer } with { briefly "a thing" }
       |    processor Proc as flow is {
       |      inlet In is command Dom.Ctx.Thing
       |      outlet Out is command Dom.Ctx.Thing
       |    } with { briefly "a flow" $processorOption }
       |  } with { briefly "a context" }
       |} with { briefly "a domain" }
       |""".stripMargin

  private def unrecognized(msgs: Messages): Messages =
    msgs.filter(_.message.contains("not a recognized RIDDL option"))

  /** Render messages for a failure clue using their TEXT only.
    *
    * Not `mkString`/`toString`: `Message.toString` goes through `ScalaRunTime._toString`, which
    * under Scala.js throws `TypeError: Cannot convert object to primitive value`. The clue then
    * blows up while REPORTING a failure, turning every case in this suite red on the JS row for a
    * reason unrelated to what it tests.
    */
  private def clue(msgs: Messages): String =
    msgs.map(_.message).mkString("\n")

  "an unrecognized option on an outlet" should {
    "draw a StyleWarning, as it does on a vital definition" in { (td: TestData) =>
      val msgs = messagesFor(model("""option zzznotanoption("x")"""), td)
      withClue(s"messages were: ${clue(msgs)}") {
        unrecognized(msgs) must not be empty
      }
    }
  }

  "the `lowering` option" should {
    "be accepted on an outlet without a StyleWarning" in { (td: TestData) =>
      // riddl-generator's documented placement: it reads the option off the outlet first.
      val msgs = messagesFor(model("""option lowering("emitter")"""), td)
      withClue(s"messages were: ${clue(msgs)}") {
        unrecognized(msgs) mustBe empty
      }
    }

    "be accepted on the processor, the form used to set it once for the whole streamlet" in {
      (td: TestData) =>
        val msgs = messagesFor(processorModel("""option lowering("outgoing")"""), td)
        withClue(s"messages were: ${clue(msgs)}") {
          unrecognized(msgs) mustBe empty
        }
    }

    "still complain about wrong arity, as `technology` does" in { (td: TestData) =>
      // Registering the name must not make it a free pass: the spec says exactly one argument.
      val msgs = messagesFor(model("option lowering"), td)
      val arity = msgs.filter { m =>
        m.message.contains("lowering") && !m.message.contains("not a recognized RIDDL option")
      }
      withClue(s"messages were: ${clue(msgs)}") {
        arity must not be empty
      }
    }
  }

  "a portlet with no metadata at all" should {
    "NOT draw a 'metadata should not be empty' warning" in { (td: TestData) =>
      // Validating portlet metadata CONTENTS must not import the expectation that portlets
      // carry metadata; ordinary inlets and outlets have none and that is correct.
      val msgs = messagesFor(processorModel(""), td)
      val portletEmpty = msgs.filter { m =>
        m.message.contains("should not be empty") && m.message.contains("Out")
      }
      withClue(s"messages were: ${clue(msgs)}") {
        portletEmpty mustBe empty
      }
    }
  }
}
