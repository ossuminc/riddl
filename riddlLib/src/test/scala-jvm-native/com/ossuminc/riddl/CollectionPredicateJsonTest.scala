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

/** B5 (2026-09-23) on the JSON surface: the four collection kinds, the quantifier by name, and
  * the round trip a fixed point.
  */
class CollectionPredicateJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record Item is { ready: Boolean, kind: String } with { briefly "i" }
      |    record Data is { items: C.Item*, zips: String* } with { briefly "d" }
      |    entity E is {
      |      state S of record C.Data
      |      handler H is {
      |        on init is {
      |          let a = all of Data.items as item where item.ready
      |          let b = Data.items as i where i.ready
      |          let c = count of Data.items
      |          let d = Data.zips contains "94110"
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

  "the collection kinds in JSON" should {

    "serialize with their kinds and the quantifier by name" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"value\":\"quantifier\"")
      json must include("\"quantifier\":\"all\"")
      json must include("\"element\":\"item\"")
      json must include("\"value\":\"filter\"")
      json must include("\"value\":\"count\"")
      json must include("\"value\":\"contains\"")
    }

    "round-trip to the same values and be a fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          shapes(back) mustBe shapes(root)
          Finder(back).recursiveFindByType[CollectionPredicate].map(_.quantifier) mustBe
            Seq(CollectionQuantifier.All)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
