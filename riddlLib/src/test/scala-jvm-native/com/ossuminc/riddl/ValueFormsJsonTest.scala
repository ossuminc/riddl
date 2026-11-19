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

/** BACKLOG [1.27] value forms on the JSON surface: each kind named, and the round trip a fixed
  * point that recovers the same values.
  */
class ValueFormsJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record Line is { qty: Integer, price: Integer, sku: String } with { briefly "l" }
      |    record Data is { lines: C.Line*, wait: Duration, t1: TimeStamp, t2: TimeStamp }
      |      with { briefly "d" }
      |    entity E is {
      |      state S of record C.Data
      |      handler H is {
      |        on init is {
      |          let a = minutes of Data.wait
      |          let b = days of (Data.t2 - Data.t1) + 1
      |          let c = sum of Data.lines
      |          let d = sum of Data.lines as l by l.qty * l.price
      |        }
      |      } with { briefly "h" }
      |    } with { briefly "e" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def shapes(root: Root): Seq[String] =
    Finder(root).recursiveFindByType[LetStatement].map(_.expression.format)

  "the value forms in JSON" should {

    "serialize with their kinds" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"value\":\"durationUnits\"")
      json must include("\"unit\":\"minutes\"")
      json must include("\"value\":\"sum\"")
      json must include("\"by\":")
    }

    "round-trip to the same values and be a fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          shapes(back) mustBe shapes(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
