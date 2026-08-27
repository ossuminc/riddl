/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.Assertion
import org.scalatest.TestData

/** `checkNonEmptyValue` asks whether a value has NO CONTENTS. Only [[LiteralString]] answers that
  * meaningfully among the [[AST.Value]] kinds.
  *
  * `RiddlValue.isEmpty` defaults to `true` and its contract is explicit — "non-containers are
  * always empty" (`AST.scala:98`). Every Value except `LiteralString` is a non-container, so asking
  * one whether it is empty always says yes. Two call sites asked anyway, on an arbitrary Value:
  * `let`'s expression and `set`'s value. The result was a MissingWarning on perfectly good code —
  * `let q = call function F(...)` and `set field S.flag to true` were both reported as empty.
  *
  * The fix is in the CALLERS, which now guard on `LiteralString` exactly as the eight neighbouring
  * call sites already did. Overriding `isEmpty` on `Call`/`Constructor`/`ValueRef`/`BooleanLiteral`
  * would have "fixed" it by redefining emptiness to mean ABSENT rather than CONTENTLESS, which is a
  * different question and one the whole traversal layer depends on (Reid, 2026-08-10).
  */
class ValueEmptinessCheckTest extends AbstractValidatingTest {

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

  private def emptinessComplaints(msgs: Messages): Messages =
    msgs.filter(_.message.contains("must not be empty"))

  private def model(statement: String): String =
    s"""domain D is {
       |  context C is {
       |    command Pay is { amount: Integer } with { briefly "p" }
       |    record Args is { a: Integer } with { briefly "a" }
       |    record Sum is { total: Integer } with { briefly "s" }
       |    record St is { total: Integer, flag: Boolean, note: String(1,20) } with { briefly "st" }
       |    function F is {
       |      requires record C.Args
       |      returns record C.Sum
       |      return record C.Sum(total = "t")
       |    } with { briefly "f" }
       |    entity Orders is {
       |      state S of record C.St is {
       |        handler H is {
       |          on command Pay is {
       |            $statement
       |          }
       |        } with { briefly "h" }
       |      } with { briefly "state" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def noComplaint(statement: String, td: TestData): Assertion =
    val msgs = messagesFor(model(statement), td)
    withClue(s"for '$statement':\n${msgs.format}\n") {
      emptinessComplaints(msgs) mustBe empty
    }

  private def complains(statement: String, td: TestData): Assertion =
    val msgs = messagesFor(model(statement), td)
    withClue(s"for '$statement':\n${msgs.format}\n") {
      emptinessComplaints(msgs) must not be empty
    }

  "A non-container value" should {

    "not be called empty as a 'let' expression (call)" in { (td: TestData) =>
      noComplaint("""let quo = call function C.F(a = "1")""", td)
    }

    "not be called empty as a 'let' expression (constructor)" in { (td: TestData) =>
      noComplaint("""let quo = record C.Sum(total = "t")""", td)
    }

    "not be called empty as a 'let' expression (boolean literal)" in { (td: TestData) =>
      noComplaint("let quo = true", td)
    }

    "not be called empty as a 'set' value (boolean literal)" in { (td: TestData) =>
      noComplaint("set field S.flag to true", td)
    }
  }

  "A genuinely empty literal" should {

    "still be reported as a 'let' expression" in { (td: TestData) =>
      complains("""let quo = "" """.trim, td)
    }

    "still be reported as a 'set' value" in { (td: TestData) =>
      complains("""set field S.note to "" """.trim, td)
    }
  }
}
