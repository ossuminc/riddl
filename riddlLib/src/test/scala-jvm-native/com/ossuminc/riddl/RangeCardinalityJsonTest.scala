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

/** The cardinality range `T{n,m}` on the JSON surface. An open maximum is Long.MaxValue, which a
  * JSON number (a Double) cannot hold exactly, so it is written by OMITTING "max", and an absent
  * "max" reads back as open.
  */
class RangeCardinalityJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record R is { a: Integer{1,3}, b: Integer{2,}, c: Integer{,5} } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def bounds(root: Root): Seq[(Long, Long)] =
    Finder(root).recursiveFindByType[Type].flatMap(_.typEx match
      case a: AggregateTypeExpression =>
        a.fields.map(_.typeEx).collect { case sr: SpecificRange => (sr.min, sr.max) }
      case _ => Seq.empty)

  "a cardinality range in JSON" should {

    "omit an open maximum" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      "\"cardinality\":\"range\"".r.findAllIn(json).size mustBe 3
      "\"max\":".r.findAllIn(json).size mustBe 2
      json must not include "9223372036854775807"
      json must not include "E18"
    }

    "round-trip to the same bounds, open ones included, and be a fixed point" in {
      val root = parse(src)
      bounds(root) mustBe Seq((1L, 3L), (2L, Long.MaxValue), (0L, 5L))
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          bounds(back) mustBe bounds(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
