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

/** The `create` value (renamed from `initiate`, Reid 2026-11-16) on the JSON surface. The writer
  * says `"value": "create"`; the reader also accepts `"initiate"` through 2.x, so JSON written before
  * the rename keeps loading.
  */
class CreateValueJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    command Go is { why: String } with { briefly "g" }
      |    record Fields is { total: String } with { briefly "f" }
      |    entity Order is {
      |      state S of record Fields is {
      |        handler H is {
      |          on init(total: String) { do "seed" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "o" }
      |    entity Caller is {
      |      state CS of record Fields is {
      |        handler CH is {
      |          on command Go { let orderId = create entity Order("5") }
      |        } with { briefly "ch" }
      |      } with { briefly "cs" }
      |    } with { briefly "c" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def creates(root: Root): Seq[String] = Finder(root).recursiveFindByType[Create].map(_.format)

  "the create value in JSON" should {
    "serialize as `create`" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"value\":\"create\"")
      json must not include "\"value\":\"initiate\""
    }

    "round-trip and be a fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          creates(back) mustBe creates(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }

    "read JSON written before the rename, which says `initiate`" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      val legacy = json1.replace("\"create\"", "\"initiate\"")
      legacy must not equal json1 // the substitution really happened
      RiddlLib.parseJson(legacy, "legacy") match
        case RiddlResult.Success(back) =>
          creates(back) mustBe creates(root)
          RiddlLib.root2Json(back) mustBe json1 // and it is written back the new way
        case RiddlResult.Failure(msgs) => fail(s"legacy JSON failed to load:\n${msgs.format}")
    }
  }
}
