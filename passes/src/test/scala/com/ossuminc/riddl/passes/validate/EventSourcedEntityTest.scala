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

/** The preconditions without which an entity cannot be event sourced.
  *
  * Replay re-applies the recorded events in order and must reproduce the SAME state changes. That
  * is only possible if every command says what event it produces (R1), every such event has a
  * clause that applies it (R2), and no state change happens anywhere but while handling one of the
  * entity's own events (R3, R4). These are Errors: a model failing them is not incompletely
  * described, it is impossible to event-source.
  */
class EventSourcedEntityTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    // Most cases below assert an ABSENCE, which a fixture that failed to parse satisfies
    // trivially. Refuse to report on a model that never parsed.
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end messagesFor

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")
  private def errs(msgs: Messages, fragment: String): Messages =
    msgs.filter(m => m.isError && m.message.contains(fragment))

  private def noYields(msgs: Messages): Messages = errs(msgs, "declares no 'yields' clause")
  private def noReplay(msgs: Messages): Messages = errs(msgs, "no 'on event' clause applies it")
  private def mutation(msgs: Messages): Messages = errs(msgs, "may only appear while handling")

  /** A conforming event-sourced entity: command declares `yields`, the handler yields it, and the
    * event's own clause performs the state change.
    */
  private val conforming: String =
    """domain D is {
      |  context C is {
      |    entity Order is {
      |      record Fields is { total: Integer } with { briefly "f" }
      |      command Place yields event Placed is { total: Integer } with { briefly "c" }
      |      event Placed is { total: Integer } with { briefly "e" }
      |      state Main of record Order.Fields is {
      |        handler H is {
      |          on command Order.Place { yield event Order.Placed } with { briefly "h1" }
      |          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "o" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def eventSourced(body: String): String = s"event-sourced $body"

  "a conforming event-sourced entity" should {
    "produce none of the four errors" in { (td: TestData) =>
      val msgs = messagesFor(conforming.replace("entity Order", "event-sourced entity Order"), td)
      withClue(s"messages were: ${clue(msgs)}") {
        noYields(msgs) mustBe empty
        noReplay(msgs) mustBe empty
        mutation(msgs) mustBe empty
      }
    }
  }

  "R1 — a handled command with no `yields` declaration" should {
    "be an error, because there is nothing to record" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace("command Place yields event Placed is", "command Place is")
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { noYields(msgs) must not be empty }
    }

    "NOT fire when the entity is not event-sourced" in { (td: TestData) =>
      val src = conforming.replace("command Place yields event Placed is", "command Place is")
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { noYields(msgs) mustBe empty }
    }
  }

  "R2 — a yielded event with no `on event` clause" should {
    "be an error, because it cannot be replayed" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace(
          """          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }""",
          ""
        )
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { noReplay(msgs) must not be empty }
    }
  }

  "R3 — a state change outside an `on event` clause" should {
    "be an error in a command clause" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace(
          "on command Order.Place { yield event Order.Placed }",
          """on command Order.Place { set field Main.total to "9" yield event Order.Placed }"""
        )
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") {
        val m = mutation(msgs)
        m must not be empty
        m.head.message must include("set")
      }
    }

    "be an error in an `on init` clause -- no lifecycle exemption" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace(
          """on command Order.Place { yield event Order.Placed } with { briefly "h1" }""",
          """on init { set field Main.total to "0" } with { briefly "h1" }"""
        )
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") { mutation(msgs) must not be empty }
    }

    "cover `morph` as well as `set`" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace(
          "on command Order.Place { yield event Order.Placed }",
          "on command Order.Place { morph entity Order to state Main with record Order.Fields yield event Order.Placed }"
        )
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") {
        mutation(msgs).exists(_.message.contains("morph")) mustBe true
      }
    }
  }

  "R4 — a FOREIGN event clause" should {

    /** `Noticed` is declared in the CONTEXT, so it is foreign to the entity. */
    def withForeign(clauseBody: String): String =
      s"""domain D is {
         |  context C is {
         |    event Noticed is { note: String } with { briefly "n" }
         |    event-sourced entity Order is {
         |      record Fields is { total: Integer } with { briefly "f" }
         |      command Place yields event Placed is { total: Integer } with { briefly "c" }
         |      event Placed is { total: Integer } with { briefly "e" }
         |      state Main of record Order.Fields is {
         |        handler H is {
         |          on command Order.Place { yield event Order.Placed } with { briefly "h1" }
         |          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }
         |          on event C.Noticed { $clauseBody } with { briefly "h3" }
         |        } with { briefly "h" }
         |      } with { briefly "s" }
         |    } with { briefly "o" }
         |  } with { briefly "c" }
         |} with { briefly "d" }
         |""".stripMargin

    "not be allowed to change state directly" in { (td: TestData) =>
      val msgs = messagesFor(withForeign("""set field Main.total to "2""""), td)
      withClue(s"messages were: ${clue(msgs)}") { mutation(msgs) must not be empty }
    }

    "be allowed to yield the entity's OWN event instead -- R4's prescribed remedy" in {
      (td: TestData) =>
        // The remedy R4 names must itself be legal, or the rule is unsatisfiable.
        val msgs = messagesFor(withForeign("yield event Order.Placed"), td)
        withClue(s"messages were: ${clue(msgs)}") { mutation(msgs) mustBe empty }
    }
  }

  "an `on event` clause for an own event outside the must-handle set" should {
    "draw no message -- R2 says which clauses must EXIST, not which may" in { (td: TestData) =>
      val src = conforming
        .replace("entity Order", "event-sourced entity Order")
        .replace(
          """          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }""",
          """          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }
            |          on event Order.Extra { set field Main.total to "2" } with { briefly "h4" }""".stripMargin
        )
        .replace(
          """      event Placed is { total: Integer } with { briefly "e" }""",
          """      event Placed is { total: Integer } with { briefly "e" }
            |      event Extra is { total: Integer } with { briefly "x" }""".stripMargin
        )
      val msgs = messagesFor(src, td)
      withClue(s"messages were: ${clue(msgs)}") {
        noReplay(msgs) mustBe empty
        mutation(msgs) mustBe empty
      }
    }
  }
}
