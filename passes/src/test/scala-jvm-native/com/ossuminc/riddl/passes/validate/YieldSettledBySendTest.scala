/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** A `send`/`tell` does NOT settle a `yields` obligation (rc.19 rule, unchanged). riddl-generator
  * found the code and its comment disagreeing on 2026-09-16 -- the comment still said "emitting
  * ANY message settles a path" and cited a `when … yield … else send … Rejected` clause; that
  * clause validates only because it ALSO opens with a `require`. The comment is corrected; this
  * pins the code, so the two cannot drift apart again in either direction.
  */
class YieldSettledBySendTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(clause: String): String =
    s"""domain D is {
       |  context Loyalty is {
       |    command Redeem yields event Loyalty.Account.Redeemed is { points: Integer } with { briefly "c" }
       |    entity Account is {
       |      record Data is { balance: Integer } with { briefly "d" }
       |      event Redeemed is { points: Integer } with { briefly "e" }
       |      event RedeemRejected is { points: Integer } with { briefly "e" }
       |      state Live of record Account.Data
       |      inlet In is command Loyalty.Redeem with { briefly "i" }
       |      outlet Rejections is event Account.RedeemRejected with { briefly "o" }
       |      handler H is {
       |        on redeem: command Loyalty.Redeem is {
       |$clause
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |  } with { briefly "l" }
       |} with { briefly "d" }
       |""".stripMargin

  private def undeclared(msgs: Messages): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(RuleId.YieldUndeclared))

  "a `when` whose else branch only SENDS a rejection" should {

    "be an Error: a transmission does not settle the yields obligation" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """          when prompt("the balance covers the points") then
            |            yield event Account.Redeemed(points = redeem.points)
            |          else
            |            send event Account.RedeemRejected(points = redeem.points) to outlet Account.Rejections
            |          end""".stripMargin
        ),
        td.name
      )
      undeclared(msgs).size mustBe 1
    }

    "validate once the failing path also REFUSES -- the shape the corpus actually writes" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """          require "the balance covers the points"
              |          yield event Account.Redeemed(points = redeem.points)""".stripMargin
          ),
          td.name
        )
        undeclared(msgs) mustBe empty
    }

    "validate with the refusal in the else branch AFTER the send (error is terminal)" in {
      (td: TestData) =>
        // `send` first, then `error`: a transmission is not an A23 effect, so it may precede the
        // refusal, and `error` ends the block, so nothing may follow it.
        val msgs = diagnostics(
          model(
            """          when prompt("the balance covers the points") then
              |            yield event Account.Redeemed(points = redeem.points)
              |          else
              |            send event Account.RedeemRejected(points = redeem.points) to outlet Account.Rejections
              |            error "insufficient balance"
              |          end""".stripMargin
          ),
          td.name
        )
        undeclared(msgs) mustBe empty
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "and the refusal-first form has no errors at all either" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """          require "the balance covers the points"
            |          yield event Account.Redeemed(points = redeem.points)""".stripMargin
        ),
        td.name
      )
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }
  }
}
