/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A qualified path to a definition declared INSIDE a State must resolve.
  *
  * The grammar lets a State contain handlers and invariants, but resolution descended into the
  * state's RECORD instead of its own contents, so `Order.Active.Strict` failed with "not found in
  * Record 'OpenState'" — pointing the author at a definition that was never the problem. The
  * relative form (`handler Strict`) always worked, which is why it went unnoticed until
  * riddl-generator wrote the explicit form.
  *
  * The record's fields must STILL resolve through the same path, hence the third case: the fix is
  * "as well as", not "instead of".
  */
class StateHandlerPathTest extends AbstractValidatingTest {

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

  private def unresolved(msgs: Messages): Messages =
    msgs.filter(_.message.contains("was not resolved"))

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  private def model(becomeTarget: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Note is { id: Integer } with { briefly "n" }
       |    command Close is { id: Integer } with { briefly "c" }
       |    entity Order is {
       |      record OpenState is { total: Integer } with { briefly "r" }
       |      initial state Active of record Order.OpenState is {
       |        initial handler ah is {
       |          on command Note { become entity Order to handler $becomeTarget }
       |        } with { briefly "h" }
       |        handler Strict is { on command Close { ??? } } with { briefly "s" }
       |      } with { briefly "st" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a QUALIFIED path to a state's handler" should {
    "resolve to the handler declared in that state" in { (td: TestData) =>
      val msgs = messagesFor(model("Order.Active.Strict"), td)
      withClue(s"messages were: ${clue(msgs)}") { unresolved(msgs) mustBe empty }
    }
  }

  "the RELATIVE form" should {
    "keep working — it is what every existing model uses" in { (td: TestData) =>
      val msgs = messagesFor(model("Strict"), td)
      withClue(s"messages were: ${clue(msgs)}") { unresolved(msgs) mustBe empty }
    }
  }

  "the state's RECORD fields" should {
    "still resolve through the state, since the fix adds rather than replaces" in {
      (td: TestData) =>
        val src =
          """domain Dom is {
            |  context Ctx is {
            |    command Note is { id: Integer } with { briefly "n" }
            |    entity Order is {
            |      record OpenState is { total: Integer } with { briefly "r" }
            |      initial state Active of record Order.OpenState is {
            |        initial handler ah is {
            |          on command Note { set field Order.Active.total to "1" }
            |        } with { briefly "h" }
            |      } with { briefly "st" }
            |    } with { briefly "e" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = messagesFor(src, td)
        withClue(s"messages were: ${clue(msgs)}") { unresolved(msgs) mustBe empty }
    }
  }
}
