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

/** Options riddl-generator relies on, and the duration check that keeps their values readable.
  *
  * Registration alone is what these first cases assert — riddlc does not act on the semantics,
  * which are a contract for the generator. The duration check is the exception: a value nobody can
  * parse has no sensible fallback, so it is caught here rather than in a generator.
  */
class OptionRegistrationTest extends AbstractValidatingTest {

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

  // Rendered from message TEXT: Message.toString is unsafe under Scala.js, and withClue
  // evaluates its argument eagerly, so interpolating the objects fails every case on the JS row.
  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  private def unrecognized(msgs: Messages): Messages =
    msgs.filter { m =>
      m.message.contains("not a recognized RIDDL option") ||
      m.message.contains("is not typically used on")
    }

  private def repositoryWith(opt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Thing is { id: Integer } with { briefly "a thing" }
       |    repository Repo is {
       |      handler Handle is {
       |        on command Dom.Ctx.Thing { do "store it" }
       |      } with { briefly "a handler" }
       |    } with { briefly "a repo" $opt }
       |  } with { briefly "a context" }
       |} with { briefly "a domain" }
       |""".stripMargin

  private def sagaWith(opt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { id: Integer } with { briefly "go" }
       |    saga Flow is {
       |      step One is { do "it" } reverted by { do "undo it" } with { briefly "a step" }
       |      step Two is { do "more" } reverted by { do "undo more" } with { briefly "a step" }
       |    } with { briefly "a saga" $opt }
       |  } with { briefly "a context" }
       |} with { briefly "a domain" }
       |""".stripMargin

  "CAP options on a Repository" should {
    "accept `available`, which hands write arbitration to the storage engine" in { (td: TestData) =>
      val msgs = messagesFor(repositoryWith("option available"), td)
      withClue(s"messages were: ${clue(msgs)}") { unrecognized(msgs) mustBe empty }
    }

    "accept `consistent`" in { (td: TestData) =>
      val msgs = messagesFor(repositoryWith("option consistent"), td)
      withClue(s"messages were: ${clue(msgs)}") { unrecognized(msgs) mustBe empty }
    }
  }

  "a saga-level timeout" should {
    "be accepted — it is the third terminal condition of a parallel saga" in { (td: TestData) =>
      val msgs = messagesFor(sagaWith("""option timeout("30s")"""), td)
      withClue(s"messages were: ${clue(msgs)}") { unrecognized(msgs) mustBe empty }
    }
  }

  "a vague duration" should {
    "be an ERROR, because a bare number is ambiguous between seconds and milliseconds" in {
      (td: TestData) =>
        val msgs = messagesFor(sagaWith("""option timeout("30")"""), td)
        val vague = msgs.filter(_.message.contains("vague duration"))
        withClue(s"messages were: ${clue(msgs)}") {
          vague must not be empty
          vague.head.isError mustBe true
        }
    }

    "reject a word that names no duration at all" in { (td: TestData) =>
      val msgs = messagesFor(sagaWith("""option timeout("soon")"""), td)
      withClue(s"messages were: ${clue(msgs)}") {
        msgs.filter(_.message.contains("vague duration")) must not be empty
      }
    }
  }

  "readable duration spellings" should {
    // riddl-generator documents all of these, so riddlc must not reject a form that already
    // works in a shipping generator.
    "all be accepted" in { (td: TestData) =>
      val forms = Seq(
        "30s",
        "1500ms",
        "5 minutes",
        "2 hours",
        "PT1M30S",
        "P1DT2H",
        "5m",
        "2h",
        "1d"
      ) // riddlg documents these single-letter forms too
      forms.foreach { form =>
        val msgs = messagesFor(sagaWith(s"""option timeout("$form")"""), td)
        withClue(s"form '$form' — messages were: ${clue(msgs)}") {
          msgs.filter(_.message.contains("vague duration")) mustBe empty
        }
      }
      succeed
    }
  }
}
