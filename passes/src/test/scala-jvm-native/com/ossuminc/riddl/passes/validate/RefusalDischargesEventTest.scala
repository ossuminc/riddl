/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Refusing a command satisfies the command→event completeness rule.
  *
  * A clause that refuses HAS processed the command: it decided, it declined, and there is nothing
  * to record. The rule was inverted before this — it flagged the honest refusal-only clause, and
  * was silenced by adding a `send` AFTER the refusal, which A23's refusals-before-effects ordering
  * makes unreachable. It rewarded dead code and penalised the correct model.
  */
class RefusalDischargesEventTest extends AbstractValidatingTest {

  private def model(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Approve is { id: String }
       |    event Approved is { id: String }
       |    entity Thing is {
       |      record Data is { id: String }
       |      state Shipped of record Dom.Ctx.Thing.Data is {
       |        initial handler H is {
       |          on command Dom.Ctx.Approve is {
       |$body
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def completenessOf(body: String, td: TestData): Seq[String] =
    var found = Seq.empty[String]
    parseAndValidateDomain(RiddlParserInput(model(body), td), shouldFailOnErrors = false) {
      case (_, _, messages) =>
        found = messages
          .filter(_.message.contains("should result in sending an event"))
          .map(_.message)
        succeed
    }
    found

  "a comments-only body" should {

    /** `isEmpty` is comment-tolerant now: a container holding nothing but comments has no
      * DEFINITIONS, so it is a stub. Before this it read as non-empty and slipped past every
      * "should not be empty" completeness check — a modeller could write `context C is { // TODO }`
      * and be told it was fine.
      */
    "be reported as empty" in { (td: TestData) =>
      val input = RiddlParserInput(
        "domain Dom is {\n  // TODO: describe the contexts\n  ???\n}\n",
        td
      )
      var msgs = Seq.empty[String]
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        msgs = messages.map(_.message).filter(_.contains("empty"))
        succeed
      }
      withClue("a comments-only body must read as empty: ") { msgs mustNot be(empty) }
    }
  }

  "the command completeness rule" should {

    "accept a clause that REFUSES with `error`" in { (td: TestData) =>
      val msgs = completenessOf("""            error "a shipped return cannot be approved"""", td)
      withClue(s"a refusal is a complete outcome: $msgs") { msgs mustBe empty }
    }

    "accept a clause that refuses with `require`" in { (td: TestData) =>
      val msgs = completenessOf("""            require "the return has not shipped"""", td)
      withClue(s"a require can refuse, so it discharges too: $msgs") { msgs mustBe empty }
    }

    "still FLAG a clause that neither refuses nor emits an event" in { (td: TestData) =>
      val msgs = completenessOf("""            do "quietly ignore it"""", td)
      withClue("a clause that does neither is genuinely incomplete") { msgs mustNot be(empty) }
    }

    "still accept a clause that sends an event" in { (td: TestData) =>
      val msgs = completenessOf(
        """            tell event Dom.Ctx.Approved to entity Dom.Ctx.Thing""",
        td
      )
      msgs mustBe empty
    }
  }
}
