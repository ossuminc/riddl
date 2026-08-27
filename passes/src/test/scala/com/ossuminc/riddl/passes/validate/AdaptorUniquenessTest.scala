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

/** A context may adapt to and from another context, but only ONCE in each direction.
  *
  * Two adaptors with the same direction to the same foreign context split that context's
  * translation across two places, and nothing says which handles a given message. Direction is part
  * of the key because the computational model §7.1 states that "a bidirectional relationship is two
  * adaptors" — inbound plus outbound is the sanctioned way to say "both ways", not duplication.
  */
class AdaptorUniquenessTest extends AbstractValidatingTest {

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

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  private def duplicates(msgs: Messages): Messages =
    msgs.filter(m => m.message.contains("duplicates") && m.message.contains("already adapts"))

  /** Two contexts, with `adaptorsInFirst` spliced into the first. */
  private def model(adaptorsInFirst: String): String =
    s"""domain Dom is {
       |  context Other is {
       |    command Foreign is { id: Integer } with { briefly "f" }
       |  } with { briefly "other" }
       |  context Mine is {
       |    command Native is { id: Integer } with { briefly "n" }
       |$adaptorsInFirst
       |  } with { briefly "mine" }
       |} with { briefly "d" }
       |""".stripMargin

  private val inbound =
    """    adaptor In from context Dom.Other is {
      |      handler H is { on command Dom.Other.Foreign { do "translate" } }
      |    } with { briefly "in" }""".stripMargin

  "two adaptors with the SAME direction to the same context" should {
    "be an error, because nothing says which one prevails" in { (td: TestData) =>
      val second =
        inbound.replace("adaptor In ", "adaptor AlsoIn ").replace("handler H ", "handler H2 ")
      val msgs = messagesFor(model(s"$inbound\n$second"), td)
      withClue(s"messages were: ${clue(msgs)}") {
        val dupes = duplicates(msgs)
        dupes must not be empty
        dupes.head.isError mustBe true
      }
    }
  }

  "one adaptor in each direction to the same context" should {
    "be legal — §7.1: a bidirectional relationship is two adaptors" in { (td: TestData) =>
      val outbound =
        """    adaptor Out to context Dom.Other is {
          |      handler O is { on command Dom.Mine.Native { do "translate" } }
          |    } with { briefly "out" }""".stripMargin
      val msgs = messagesFor(model(s"$inbound\n$outbound"), td)
      withClue(s"messages were: ${clue(msgs)}") { duplicates(msgs) mustBe empty }
    }
  }

  "a single adaptor" should {
    "be legal" in { (td: TestData) =>
      val msgs = messagesFor(model(inbound), td)
      withClue(s"messages were: ${clue(msgs)}") { duplicates(msgs) mustBe empty }
    }
  }

  "adaptors owned by DIFFERENT contexts" should {
    "both be legal — each context defends its own model" in { (td: TestData) =>
      // Mine adapts from Other, and Other adapts from Mine. Different owners, so this is two
      // contexts each declaring its own anti-corruption layer, not duplication.
      val src =
        """domain Dom is {
          |  context Other is {
          |    command Foreign is { id: Integer } with { briefly "f" }
          |    adaptor Back from context Dom.Mine is {
          |      handler B is { on command Dom.Mine.Native { do "translate" } }
          |    } with { briefly "back" }
          |  } with { briefly "other" }
          |  context Mine is {
          |    command Native is { id: Integer } with { briefly "n" }
          |    adaptor In from context Dom.Other is {
          |      handler H is { on command Dom.Other.Foreign { do "translate" } }
          |    } with { briefly "in" }
          |  } with { briefly "mine" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { duplicates(msgs) mustBe empty }
    }
  }
}
