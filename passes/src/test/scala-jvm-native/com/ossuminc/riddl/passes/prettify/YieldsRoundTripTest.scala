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

/** RIDDL is reflective: A19's `yields` clause on a command/query type must emit (prettify) and
  * re-parse to the same shape (same yielded message ref at the same place).
  */
class YieldsRoundTripTest extends AbstractValidatingTest {

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
    """domain d is { context c is {
      |  event OrderPlaced is { id: Integer }
      |  result OrderFound is { id: Integer }
      |  command PlaceOrder yields event OrderPlaced is { id: Integer }
      |  query FindOrder yields result OrderFound is { id: Integer }
      |  command CancelOrder is { id: Integer }
      |}}
      |""".stripMargin

  "yields clause" should {
    "round-trip a command/query yields clause through prettify" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("yields event OrderPlaced")
      pretty must include("yields result OrderFound")

      val types = Finder(parse(pretty, "regen")).recursiveFindByType[Type]

      val cmd = types.find(_.id.value == "PlaceOrder").get
      cmd.typEx.asInstanceOf[AggregateUseCaseTypeExpression].yields match
        case Some(EventRef(_, pid)) => pid.value.last mustBe "OrderPlaced"
        case other                  => fail(s"Expected EventRef, got $other")

      val qry = types.find(_.id.value == "FindOrder").get
      qry.typEx.asInstanceOf[AggregateUseCaseTypeExpression].yields match
        case Some(ResultRef(_, pid)) => pid.value.last mustBe "OrderFound"
        case other                   => fail(s"Expected ResultRef, got $other")

      // A plain command with no yields stays None after round-trip.
      val plain = types.find(_.id.value == "CancelOrder").get
      plain.typEx.asInstanceOf[AggregateUseCaseTypeExpression].yields mustBe None
    }
  }
}
