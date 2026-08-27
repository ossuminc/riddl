/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** RIDDL is reflective: the `initial` marker on states/handlers (#14) must emit (prettify) and
  * re-parse back to the same flags. Prettify makes the choice explicit — even the defaulted-first —
  * which is the whole point (refactor-safe under reordering).
  */
class InitialMarkerRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
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

  private val src =
    """domain d is { context c is { entity e is {
      |  type Data is { x: Integer }
      |  state First of record d.c.e.Data is {
      |    handler H1 is { on other is { do "a" } }
      |    initial handler H2 is { on other is { do "b" } }
      |  }
      |  initial state Second of record d.c.e.Data is {
      |    handler H3 is { on other is { do "c" } }
      |  }
      |}}}
      |""".stripMargin

  "Initial marker" should {
    "round-trip explicit and defaulted `initial` markers through prettify" in { (td: TestData) =>
      val e1 = Finder(parse(src, "src")).recursiveFindByType[Entity].head
      e1.states.find(_.id.value == "Second").get.isInitial mustBe true
      e1.states
        .find(_.id.value == "First")
        .get
        .handlers
        .find(_.id.value == "H2")
        .get
        .isInitial mustBe true

      val pretty = prettify(parse(src, "src"))
      // `of` introduces the record reference; `is` introduces the body. This asserted the
      // deprecated spelling, which prettify emitted for a BODIED state until that was corrected.
      pretty must include("initial state Second of")
      pretty must include("initial handler H2 is")
      // the defaulted-first handler of Second is emitted explicit, too
      pretty must include("initial handler H3 is")

      // Re-parsed: the markers survive exactly (explicit stay explicit; the defaulted one is now
      // explicit but the flag is identical).
      val e2 = Finder(parse(pretty, "regen")).recursiveFindByType[Entity].head
      e2.states.find(_.id.value == "Second").get.isInitial mustBe true
      e2.states.find(_.id.value == "First").get.isInitial mustBe false
      val first = e2.states.find(_.id.value == "First").get
      first.handlers.find(_.id.value == "H2").get.isInitial mustBe true
      first.handlers.find(_.id.value == "H1").get.isInitial mustBe false
      e2.states.find(_.id.value == "Second").get.handlers.head.isInitial mustBe true
    }
  }
}
