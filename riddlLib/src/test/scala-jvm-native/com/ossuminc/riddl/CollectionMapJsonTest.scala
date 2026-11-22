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

/** The collection map (`each of … yielding`) on the JSON surface: named by its kind, with the
  * filter key present only when there is a filter, and the round trip a fixed point.
  */
class CollectionMapJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record Line is { qty: Integer, sku: String } with { briefly "l" }
      |    record Data is { lines: C.Line* } with { briefly "d" }
      |    entity E is {
      |      state S of record C.Data
      |      handler H is {
      |        on init is {
      |          let a = each of Data.lines as l yielding l.sku
      |          let b = each of Data.lines as l where l.qty > 0 yielding l.qty * 2
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

  "the collection map in JSON" should {

    "serialize with its kind, and a filter only when written" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"value\":\"map\"")
      json must include("\"element\":\"l\"")
      json must include("\"projection\":")
      "\"filter\":".r.findAllIn(json).size mustBe 1
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
