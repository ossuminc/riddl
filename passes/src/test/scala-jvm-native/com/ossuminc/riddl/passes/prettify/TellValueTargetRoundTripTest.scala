/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `tell <msg> to <value>` widened `TellStatement.target` to a union, so RIDDL's reflectivity
  * mandate requires a prettify round trip proving BOTH shapes survive at the same place.
  *
  * The value shape is the one at risk: it is emitted through `emitValue` rather than `.format`,
  * because a value can contain a nested `prompt(...) as T` whose narrower `.format` copy can
  * produce source that does not re-parse.
  */
class TellValueTargetRoundTripTest extends AbstractValidatingTest {

  private def src(target: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    type SiteId is Id(entity Ctx.CampSite) with { briefly "id" }
       |    command Ping is { note: String } with { briefly "p" }
       |    record R is { siteId: SiteId } with { briefly "r" }
       |    entity CampSite is {
       |      state CS of record R is {
       |        handler H is { on command Ping { do "note" } } with { briefly "h" }
       |      } with { briefly "cs" }
       |    } with { briefly "e" }
       |    entity Booking is {
       |      state BS of record R is {
       |        handler BH is {
       |          on init {
       |            tell command Ping(note = "hi") to $target
       |          }
       |        } with { briefly "bh" }
       |      } with { briefly "bs" }
       |    } with { briefly "be" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def tellIn(root: Root): TellStatement =
    Finder(root)
      .recursiveFindByType[TellStatement]
      .headOption
      .getOrElse(fail("no TellStatement found"))

  "a value target" should {
    "round-trip a field reference through prettify" in { (td: TestData) =>
      val original = parse(src("Booking.BS.siteId"), "src")
      tellIn(original).target mustBe a[ValueRef]

      val pretty = prettify(original)
      pretty must include("to Booking.BS.siteId")
      // It must NOT acquire a keyword: `to entity Booking.BS.siteId` would name a field as an
      // entity, and would re-parse as a ProcessorRef -- a DIFFERENT AST.
      pretty must not include "to entity Booking.BS.siteId"

      val regen = parse(pretty, "regen")
      regen.isEmpty mustBe false
      tellIn(regen).target mustBe a[ValueRef]
      tellIn(regen).target.format mustBe tellIn(original).target.format
    }

    "round-trip `self.id` through prettify" in { (td: TestData) =>
      val original = parse(src("self.id"), "src")
      tellIn(original).target mustBe a[SelfValue]

      val pretty = prettify(original)
      pretty must include("to self.id")

      val regen = parse(pretty, "regen")
      tellIn(regen).target mustBe a[SelfValue]
      tellIn(regen).target.format mustBe tellIn(original).target.format
    }
  }

  "the static form" should {
    "still round-trip, keeping its keyword" in { (td: TestData) =>
      val original = parse(src("entity Ctx.CampSite"), "src")
      tellIn(original).target mustBe a[EntityRef]

      val pretty = prettify(original)
      pretty must include("to entity Ctx.CampSite")

      val regen = parse(pretty, "regen")
      tellIn(regen).target mustBe a[EntityRef]
      tellIn(regen).target.format mustBe tellIn(original).target.format
    }
  }
}
