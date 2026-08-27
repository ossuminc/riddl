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

/** A path may descend into a field's type only when the field denotes EXACTLY ONE value.
  *
  * A cardinality wrapper says otherwise: `?` means the value may be absent, `*` and `+` mean there
  * are many. In none of those cases is there a single value for `a.b.c` to reach through, so
  * refusing to descend is correct and stays correct (Reid, 2026-08-10).
  *
  * What was wrong is the DIAGNOSTIC. `candidatesFromTypeExpression` returns no candidates for a
  * [[com.ossuminc.riddl.language.AST.Cardinality]], and the caller then reported "the name 'x' was
  * not found in Field 'y'" — sending the author to hunt a typo that does not exist, when the name
  * IS in the type and the cardinality is what stopped the walk. Reported from riddl-models while
  * adopting `foreach` destructuring, where the natural home for a mapping sat behind an optional.
  */
class CardinalityPathDescentTest extends AbstractValidatingTest {

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

  /** `transformation` carries whatever cardinality is under test; the path always tries to reach
    * `valueMap` through it, so the cardinality is the only variable.
    */
  private def model(cardinality: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    type Inner is { valueMap: mapping from String to String } with { briefly "i" }
       |    type Outer is { transformation: Inner$cardinality } with { briefly "o" }
       |    command Cmd is { payload: Outer } with { briefly "c" }
       |    entity E is {
       |      handler H is {
       |        on command Cmd {
       |          foreach k, v in field Cmd.payload.transformation.valueMap {
       |            do "use it"
       |          }
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "ctx" }
       |} with { briefly "d" }
       |""".stripMargin

  "Descending a path through a field" should {

    "succeed when the field denotes exactly one value" in { (td: TestData) =>
      val msgs = messagesFor(model(""), td)
      withClue(clue(msgs)) { unresolved(msgs) mustBe empty }
    }

    "refuse through an optional field, and say the cardinality is why" in { (td: TestData) =>
      val msgs = messagesFor(model("?"), td)
      val failures = unresolved(msgs)
      withClue(clue(msgs)) {
        failures must not be empty
        // The real reason, not "the name was not found".
        failures.exists(_.message.contains("is optional")) mustBe true
        failures.exists(_.message.contains("was not found in")) mustBe false
      }
    }

    "refuse through a zero-or-more field, and say the cardinality is why" in { (td: TestData) =>
      val msgs = messagesFor(model("*"), td)
      val failures = unresolved(msgs)
      withClue(clue(msgs)) {
        failures must not be empty
        failures.exists(_.message.contains("holds many values")) mustBe true
        failures.exists(_.message.contains("was not found in")) mustBe false
      }
    }

    "refuse through a one-or-more field, and say the cardinality is why" in { (td: TestData) =>
      val msgs = messagesFor(model("+"), td)
      val failures = unresolved(msgs)
      withClue(clue(msgs)) {
        failures must not be empty
        failures.exists(_.message.contains("holds many values")) mustBe true
        failures.exists(_.message.contains("was not found in")) mustBe false
      }
    }

    "still say 'not found' when the name genuinely is not in the type" in { (td: TestData) =>
      val src = model("").replace("transformation.valueMap", "transformation.noSuchField")
      val msgs = messagesFor(src, td)
      val failures = unresolved(msgs)
      withClue(clue(msgs)) {
        failures.exists(_.message.contains("was not found in")) mustBe true
      }
    }
  }
}
