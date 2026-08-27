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

/** A value reference may only read the state record IN SCOPE on its path (riddl-models,
  * 2026-08-25).
  *
  * Inside `state Confirmed of record ConfirmedData`, `base = SeatedData.base` names a DIFFERENT
  * state's record. Nothing rejected it, because value references resolve by whether the field
  * EXISTS — the diagnostic literally says "a field of the handled message or entity state" — not by
  * whether that record is the state currently occupied.
  *
  * **An Error, because the read has no defined source.** Only one state is occupied at a time, so
  * the other state's record holds nothing on this path. A generator lowers a carry-forward by
  * reading the pre-transition record, so a wrong one lowers to code that compiles and is silently
  * wrong.
  */
class StateRecordInScopeTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def errs(msgs: Messages): Messages =
    msgs.filter(m => m.isError && m.message.contains("holds nothing here"))

  /** Two states, each with its own record; the handler lives inside `Confirmed`. */
  private def model(read: String, handlerIn: String = "Confirmed"): String =
    s"""domain D is {
       |  context C is {
       |    command Go is { g: String(1,9) }
       |    record SeatedData is { base: String(1,9) }
       |    record ConfirmedData is { base: String(1,9) }
       |    entity Table is {
       |      state Seated of record C.SeatedData is {${if handlerIn == "Seated" then handler(read) else " ??? "}}
       |      state Confirmed of record C.ConfirmedData is {${if handlerIn == "Confirmed" then handler(read) else " ??? "}}
       |    }
       |  }
       |}
       |""".stripMargin

  private def handler(read: String): String =
    s"""
       |        handler H is {
       |          on command C.Go is {
       |            morph entity C.Table to state Table.Seated with record C.SeatedData(base = $read)
       |          }
       |        }
       |      """.stripMargin

  "a value reference in a state's handler" should {

    "be an Error when it reads ANOTHER state's record" in { (td: TestData) =>
      val msgs = messagesFor(model("SeatedData.base"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs)
        hit must not be empty
        // Names both sides: which record, and which state it belongs to.
        hit.head.message must include("SeatedData")
        hit.head.message must include("Seated")
        hit.head.message must include("Confirmed")
      }
    }

    "draw nothing when it reads the record IN SCOPE" in { (td: TestData) =>
      val msgs = messagesFor(model("ConfirmedData.base"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    "draw nothing when the same record IS the state in scope" in { (td: TestData) =>
      // The handler moved into `Seated`, so reading SeatedData is now correct — the rule is about
      // the state on THIS path, not about which record a name happens to be.
      val msgs = messagesFor(model("SeatedData.base", handlerIn = "Seated"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    "draw nothing for a field of the handled MESSAGE" in { (td: TestData) =>
      // Only state records are in question. A message field is in scope by definition.
      val msgs = messagesFor(model("Go.g"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    /** The entity-level case, which the rule above deliberately leaves alone.
      *
      * riddl-models' largest single finding: a clause that ESTABLISHES a state reading that state's
      * record in the same breath. Their underlying cause was usually real — the creating COMMAND
      * carried the data and the EVENT dropped it, so the clause reached for the only thing in
      * scope.
      *
      * Selected by STRUCTURE, never by name: their own first attempt matched `Create*`/`*Created`
      * and missed `EnrollCustomer` and `InventoryItemInitialized`. The structural fact is that the
      * morph's target record and the read record are the SAME definition.
      */
    "be an Error when an entity-level clause reads the record its morph is establishing" in {
      (td: TestData) =>
        val src =
          """domain D is {
            |  context C is {
            |    event Created is { name: String(1,9) }
            |    record ItemData is { recipe: String(1,9)  name: String(1,9) }
            |    record OtherData is { x: String(1,9) }
            |    entity Item is {
            |      state Live of record C.ItemData is { ??? }
            |      state Gone of record C.OtherData is { ??? }
            |      handler H is {
            |        on event C.Created is {
            |          morph entity C.Item to state Item.Live with record C.ItemData(recipe = ItemData.recipe, name = Created.name)
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        val msgs = messagesFor(src, td)
        withClue(msgs.map(_.message).mkString("\n")) {
          val hit = msgs.filter(m => m.isError && m.message.contains("holds nothing yet"))
          hit must not be empty
          hit.head.message must include("ItemData")
          hit.head.message must include("Live")
        }
    }

    "draw nothing when that clause reads the MESSAGE instead" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event Created is { name: String(1,9) }
          |    record ItemData is { recipe: String(1,9)  name: String(1,9) }
          |    record OtherData is { x: String(1,9) }
          |    entity Item is {
          |      state Live of record C.ItemData is { ??? }
          |      state Gone of record C.OtherData is { ??? }
          |      handler H is {
          |        on event C.Created is {
          |          morph entity C.Item to state Item.Live with record C.ItemData(recipe = Created.name, name = Created.name)
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) {
        msgs.filter(_.message.contains("holds nothing yet")) mustBe empty
      }
    }

    "not be the rule that fires in an entity-level handler, which has no state in scope" in {
      (td: TestData) =>
      // THIS rule stays silent: an entity-level handler could be in ANY of the entity's states, so
      // "the record in scope" has no answer, and reporting would be reasoning from absence.
      //
      // The model below is nonetheless an error — from the CREATION rule above, which owns exactly
      // this case. Asserting only that the in-scope rule is quiet keeps the two separable; without
      // this note the case reads as "no diagnostic here", which is not true.
      val src =
        """domain D is {
          |  context C is {
          |    command Go is { g: String(1,9) }
          |    record SeatedData is { base: String(1,9) }
          |    record ConfirmedData is { base: String(1,9) }
          |    entity Table is {
          |      state Seated of record C.SeatedData is { ??? }
          |      state Confirmed of record C.ConfirmedData is { ??? }
          |      handler H is {
          |        on command C.Go is {
          |          morph entity C.Table to state Table.Seated with record C.SeatedData(base = SeatedData.base)
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }
  }
}
