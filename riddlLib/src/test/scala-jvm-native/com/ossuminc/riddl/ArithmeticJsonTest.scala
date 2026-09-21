/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B4 (2026-09-21) on the JSON surface: `"value": "arithmetic"` and `"duration"`, a
  * `constantRef` that comes BACK as a `ConstantRef` (it used to degrade to a ValueRef), a
  * comparison whose operands are values, and the round trip a fixed point. The duration's
  * amount is asserted as TEXT -- a consistently mangled `1.5` would still be a fixed point.
  */
class ArithmeticJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    constant A: Natural = 10 with { briefly "a" }
      |    constant R: Natural = constant A * 2 with { briefly "r" }
      |    constant W: Duration = 1.50 hours with { briefly "w" }
      |    handler H is {
      |      on init {
      |        let v = (a + b) * c - d / 2
      |        let t = system.now + 30 days
      |        when a + b > "x" + y then { do "x" } end
      |      }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def shapes(root: Root): Seq[String] =
    Finder(root).recursiveFindByType[LetStatement].map(_.expression.format) ++
      Finder(root).recursiveFindByType[WhenStatement].map(_.condition.format) ++
      Finder(root).recursiveFindByType[Constant].map(_.value.format)

  "B4 values in JSON" should {

    "serialize with their kinds, the duration amount as text" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"value\":\"arithmetic\"")
      json must include("\"value\":\"duration\"")
      json must include("\"amount\":\"1.50\"")
      json must include("\"unit\":\"hours\"")
      json must include("\"value\":\"constantRef\"")
    }

    "round-trip to the same nodes, a ConstantRef staying a ConstantRef, and be a fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          shapes(back) mustBe shapes(root)
          Finder(back).recursiveFindByType[Constant].apply(1).value match
            case ArithmeticExpression(_, _, _: ConstantRef, _) => succeed
            case other => fail(s"constant R read back as ${other.getClass.getSimpleName}")
          Finder(back).recursiveFindByType[DurationLiteral].map(_.amount.text) mustBe Seq("1.50", "30")
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
