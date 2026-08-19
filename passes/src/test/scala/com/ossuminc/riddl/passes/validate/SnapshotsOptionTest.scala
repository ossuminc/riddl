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

/** `option snapshots` — the model says WHETHER journal-derived snapshots are taken.
  *
  * Requested by riddl-generator 2026-08-19: it had been deciding for the modeller, silently, by
  * making the row the snapshot. That is a trade the generator cannot weigh — it turns on update
  * rate, read/write mix and physical layout, none of which is in the model.
  *
  * **Absence is meaningful and is the default (author's ruling): take NO snapshots, and rehydrate
  * by replaying every event.** Many entities see fewer than a hundred events in their whole
  * lifespan, and an ephemeral one goes through a handful of transitions before terminating —
  * snapshotting those buys nothing.
  *
  * **Entity, and only an EVENT-SOURCED one.** Snapshotting a journal means nothing where there is
  * no journal, so this is an Error rather than a parent-kind style nudge — the same reasoning that
  * made a misplaced `persistent` an Error rather than a warning.
  */
class SnapshotsOptionTest extends AbstractValidatingTest {

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

  /** A conforming event-sourced entity — R1..R4 satisfied — with `intentions` and `opts` spliced. */
  private def model(intentions: String, opts: String): String =
    s"""domain D is {
       |  context C is {
       |    ${intentions}entity Order is {
       |      record Fields is { total: Integer } with { briefly "f" }
       |      command Place yields event Placed is { total: Integer } with { briefly "c" }
       |      event Placed is { total: Integer } with { briefly "e" }
       |      state Main of record Order.Fields is {
       |        handler H is {
       |          on command Order.Place { yield event Order.Placed } with { briefly "h1" }
       |          on event Order.Placed { set field Main.total to "1" } with { briefly "h2" }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "o"$opts }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def snapshotErrors(msgs: Messages): Messages =
    msgs.filter(_.message.contains("snapshots"))

  "option snapshots on an event-sourced entity" should {
    "be accepted, and be a RECOGNIZED option" in { (td: TestData) =>
      val msgs = messagesFor(model("event-sourced ", " option snapshots"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        snapshotErrors(msgs).filter(_.isError) mustBe empty
        // If the registry entry were missing this would be "not a recognized RIDDL option".
        msgs.filter(_.message.contains("not a recognized")) mustBe empty
      }
    }
  }

  "option snapshots on an entity that is NOT event-sourced" should {
    "be an Error — there is no journal to snapshot" in { (td: TestData) =>
      val msgs = messagesFor(model("", " option snapshots"), td)
      val found = snapshotErrors(msgs).filter(_.isError)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("event-sourced")
      }
    }

    "be an Error on a `persistent` entity too, not merely on a default one" in { (td: TestData) =>
      val msgs = messagesFor(model("persistent ", " option snapshots"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        snapshotErrors(msgs).filter(_.isError) must not be empty
      }
    }
  }

  "an event-sourced entity with NO snapshots option" should {
    "draw nothing — absence is the default and means replay the whole log" in { (td: TestData) =>
      val msgs = messagesFor(model("event-sourced ", ""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        snapshotErrors(msgs) mustBe empty
      }
    }
  }
}
