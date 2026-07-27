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

/** A18: an `invariant` may be declared inside a `state` body. RIDDL is reflective, so a
  * state-scoped invariant must emit (prettify) and survive a parse -> prettify -> re-parse
  * round-trip AT the same place — inside the state, not dropped and not relocated up to the entity.
  */
class StateInvariantRoundTripTest extends AbstractValidatingTest {

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
      |  state S of record d.c.e.Data is {
      |    invariant nonNegative is "x must be >= 0" with { briefly "state constraint" }
      |    handler H is { on other is { do "a" } }
      |  }
      |}}}
      |""".stripMargin

  "State-scoped invariant" should {
    "parse into the state's invariants accessor" in { (td: TestData) =>
      val e = Finder(parse(src, "src")).recursiveFindByType[Entity].head
      val s = e.states.find(_.id.value == "S").getOrElse(fail("state S missing"))
      s.invariants.map(_.id.value) mustBe Seq("nonNegative")
      s.invariants.head.condition.collect { case ls: LiteralString => ls.s } mustBe Some(
        "x must be >= 0"
      )
      // The invariant lives in the state, not at the entity level.
      e.invariants mustBe empty
    }

    "round-trip through prettify, keeping the invariant inside the state" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("invariant nonNegative is")

      val e2 = Finder(parse(pretty, "regen")).recursiveFindByType[Entity].head
      val s2 = e2.states.find(_.id.value == "S").getOrElse(fail("state S lost after prettify"))
      s2.invariants.map(_.id.value) mustBe Seq("nonNegative")
      // Not relocated up to the entity.
      e2.invariants mustBe empty
    }
  }
}
