/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** `adaptor-direction-advisory` after A103 (riddl-models' task, 2026-09-07).
  *
  * The advisory asked whether an adaptor's ON-CLAUSES name a message type of the context it is
  * declared toward. Under A103 an OUTBOUND adaptor handles its OWN context's event and PRODUCES the
  * far context's command -- the far type appears in a `let` ascription and a `send`/`tell`, never in
  * an `on` clause -- so the advisory fired on every correctly migrated outbound adaptor and stayed
  * quiet on the unmigrated placeholders. It rewarded the wrong shape. A reference to the target
  * context ANYWHERE in the adaptor now counts: handled types, transmitted operand types (resolved
  * through the clause binding and `let`s), `let` ascriptions, and declared portlet types.
  *
  * Found alongside: the advisory resolved the referent with the adaptor's PARENTS as the refMap key,
  * while `ResolutionPass` records an adaptor's `referent` under the adaptor itself. A qualified
  * referent path (`to context D.Far`) therefore missed the lookup and the advisory was silently
  * skipped; only a bare `to context Far` ever ran it. It is resolved parent-independently now, so
  * the negative control below uses a QUALIFIED path on purpose.
  */
class AdaptorDirectionAdvisoryTest extends AbstractValidatingTest {

  private def model(alertBody: String, referent: String = "D.NotificationService"): String =
    s"""domain D is {
       |  context AlertContext is {
       |    event AlertCreated is { id: String } with { briefly "e" }
       |    command Escalate is { id: String } with { briefly "c" }
       |    inlet In is command Escalate with { briefly "i" }
       |    handler AlertHandler is { on command Escalate is { do "escalate" } } with { briefly "h" }
       |    adaptor ToNotificationService to context $referent as flow is {
       |$alertBody
       |    } with { briefly "a" }
       |  } with { briefly "alerts" }
       |  external context NotificationService is {
       |    command SendAlertNotification is { id: String } with { briefly "c" }
       |    event Notified is { id: String } with { briefly "e" }
       |    inlet In is command SendAlertNotification with { briefly "i" }
       |    handler H is { on command SendAlertNotification is { do "notify" } } with { briefly "h" }
       |  } with { briefly "ns" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def advisories(msgs: Messages): Seq[Message] =
    msgs.filter(_.ruleId.contains(RuleId.AdaptorDirectionAdvisory))

  "the adaptor-direction advisory" should {

    "NOT fire on an A103 outbound adaptor: own event handled, far command produced via `let` + `send`" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """      outlet Out is command D.NotificationService.SendAlertNotification with { briefly "o" }
              |      handler H is {
              |        on alertCreated: event AlertCreated is {
              |          let msg: type D.NotificationService.SendAlertNotification = prompt("translate it")
              |          send msg to outlet Out
              |        }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }""".stripMargin
          ),
          "advisory-let-send"
        )
        advisories(msgs) mustBe empty
    }

    "NOT fire on the same A103 shape with a BARE referent, the path the old lookup did resolve" in {
      (td: TestData) =>
        // This is the case that is RED before the fix: with a bare referent the advisory runs, and
        // it sees only the `on` clause's own-context event.
        val msgs = diagnostics(
          model(
            """      outlet Out is command D.NotificationService.SendAlertNotification with { briefly "o" }
              |      handler H is {
              |        on alertCreated: event AlertCreated is {
              |          let msg: type D.NotificationService.SendAlertNotification = prompt("translate it")
              |          send msg to outlet Out
              |        }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }""".stripMargin,
            referent = "NotificationService"
          ),
          "advisory-let-send-bare"
        )
        advisories(msgs) mustBe empty
    }

    "NOT fire on an outbound adaptor that tells a far command as a constructor" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """      handler H is {
            |        on alertCreated: event AlertCreated is {
            |          tell command D.NotificationService.SendAlertNotification(id = alertCreated.id) to context D.NotificationService
            |        }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }""".stripMargin
        ),
        "advisory-constructor-tell"
      )
      advisories(msgs) mustBe empty
    }

    "NOT fire when only a declared portlet names the far context's type" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """      outlet Out is command D.NotificationService.SendAlertNotification with { briefly "o" }
            |      handler H is {
            |        on other is { do "translate whatever arrives" }
            |      } with { briefly "h" }""".stripMargin
        ),
        "advisory-portlet-only"
      )
      advisories(msgs) mustBe empty
    }

    "still fire when the adaptor references the far context NOWHERE, even with a QUALIFIED referent" in {
      (td: TestData) =>
        // Negative control -- and the qualified `D.NotificationService` path is the one the old
        // lookup silently skipped, so this case also pins the resolution fix.
        val msgs = diagnostics(
          model(
            """      handler H is {
              |        on alertCreated: event AlertCreated is {
              |          tell command Escalate(id = alertCreated.id) to context D.AlertContext
              |        }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }""".stripMargin
          ),
          "advisory-references-nothing"
        )
        advisories(msgs) must not be empty
    }

    "still fire with a BARE referent path when nothing references the far context" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """      handler H is {
              |        on alertCreated: event AlertCreated is {
              |          tell command Escalate(id = alertCreated.id) to context D.AlertContext
              |        }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }""".stripMargin,
            referent = "NotificationService"
          ),
          "advisory-references-nothing-bare"
        )
        advisories(msgs) must not be empty
    }
  }
}
