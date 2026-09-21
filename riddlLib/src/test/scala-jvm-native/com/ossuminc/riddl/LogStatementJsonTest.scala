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

/** B7 (2026-09-21) on the JSON surface: `"kind": "log"`, the operand a value, the round trip a
  * fixed point.
  */
class LogStatementJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Ops is {
      |  context Printing is {
      |    command Print is { jobId: String, pages: Natural } with { briefly "c" }
      |    handler H is {
      |      on p: command Print is {
      |        log "printing job " + p.jobId
      |        log p.pages
      |      }
      |    } with { briefly "h" }
      |  } with { briefly "p" }
      |} with { briefly "o" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def logs(root: Root): Seq[String] = Finder(root).recursiveFindByType[LogStatement].map(_.format)

  "log in JSON" should {

    "serialize with its kind" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json.split("\"kind\":\"log\"").length - 1 mustBe 2
    }

    "round-trip to the same statements and be a JSON fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          logs(back) mustBe logs(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
