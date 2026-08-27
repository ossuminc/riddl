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

/** `tell <msg> to <value typed Id(...)>` — addressing an INSTANCE (Reid, 2026-08-20).
  *
  * *"A simple value expression could hold an `Id(entity E)` that indicates the instance to tell."*
  * Reported by riddl-generator, whose `gen riddl` produced exactly this shape from English and had
  * it rejected: requiring a processor NAME meant a handler could only ever address a statically
  * named processor, never the instance its own state refers to — the ordinary case in an aggregate
  * holding a reference to another. `terminate` already took a value target, so the capability
  * existed and the question was only why `tell` did not share it.
  *
  * **The instance is deliberately never resolved.** Which `CampSite` an `Id(entity CampSite)` holds
  * is a runtime value, and no check asks: every question posed of a tell target is answered by the
  * processor KIND, which the `Id` names structurally.
  *
  * **`tell` does NOT inherit `terminate`'s entity-only rule.** Only an entity can be *ended*, but
  * any processor can be *addressed* — a singleton's `Id` denotes its singular deployment.
  */
class TellValueTargetTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  private def errorsFor(msgs: Messages): Messages = msgs.filter(_.isError)

  /** `siteType` is the declared type of `Booking`'s `siteId` field; `target` is what the tell says. */
  private def model(target: String, siteType: String = "SiteId"): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    type SiteId is Id(entity Ctx.CampSite)
       |    command UpdateSiteStatus is { status: String(1,20) }
       |    command CheckInGuest is { who: String(1,20) }
       |    entity CampSite is {
       |      inlet Arrivals is command Ctx.UpdateSiteStatus
       |      handler CH is { on command Ctx.UpdateSiteStatus is { do "note it" } }
       |    }
       |    entity Booking is {
       |      record Data is { siteId: $siteType }
       |      state Main of record Booking.Data is {
       |        handler BH is {
       |          on command Ctx.CheckInGuest is {
       |            tell command Ctx.UpdateSiteStatus(status = "occupied") to $target
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a tell to a field typed Id(entity E)" should {
    "be accepted — this is riddl-generator's reported case, verbatim" in { (td: TestData) =>
      val msgs = messagesFor(model("Booking.Main.siteId"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errorsFor(msgs) mustBe empty }
    }

    "follow a declared alias, which is the corpus house style" in { (td: TestData) =>
      // `type SiteId is Id(entity CampSite)` -- matching a bare UniqueId alone would recognise only
      // the rare inline spelling, the trap `isAddressFieldFor` fell into.
      val msgs = messagesFor(model("Booking.Main.siteId"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errorsFor(msgs).filter(_.message.contains("requires a value of type")) mustBe empty
      }
    }
  }

  "a tell to `self.id`" should {
    "be accepted with NO lookup — the enclosing processor is on the parent stack" in {
      (td: TestData) =>
        val msgs = messagesFor(model("self.id"), td)
        withClue(msgs.map(_.message).mkString("\n")) { errorsFor(msgs) mustBe empty }
    }
  }

  "a tell to a value that is not an Id" should {
    "be an Error naming the offending type and both remedies" in { (td: TestData) =>
      val msgs = messagesFor(model("Booking.Main.siteId", siteType = "String(1,9)"), td)
      val found = errorsFor(msgs).filter(_.message.contains("requires a value of type"))
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("String(1,9)")
        found.head.suggestion must include("self.id")
        found.head.suggestion must include("to entity Order")
      }
    }
  }

  "the static form" should {
    "still work — the union did not displace it" in { (td: TestData) =>
      val msgs = messagesFor(model("entity Ctx.CampSite"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errorsFor(msgs) mustBe empty }
    }
  }

  "the structural addressing analysis" should {
    "NOT demand an address field when the value states the address" in { (td: TestData) =>
      // `checkTellAddressing` exists because a statically-named tell does not say WHICH instance,
      // so the address must be recovered from a message field typed `Id(target)`. A value target
      // states it outright, so demanding the field would be asking for something the statement has
      // made unnecessary. `UpdateSiteStatus` carries no Id field at all.
      val msgs = messagesFor(model("Booking.Main.siteId"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        msgs.filter(_.message.contains("which CampSite instance this addresses")) mustBe empty
      }
    }
  }
}
