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

/** B3 (2026-09-21) on the JSON surface: `keys` and `history` on a schema, round trip a fixed point. */
class SchemaKeysJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Kitchen is {
      |  context Tickets is {
      |    record StoredTicket is { ticketId: String, status: String } with { briefly "t" }
      |    repository Store is {
      |      schema S is relational
      |        of tickets as record Tickets.StoredTicket with history
      |        key on field Tickets.StoredTicket.ticketId
      |      with { briefly "keyed" }
      |    } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def shape(root: Root): (Seq[String], Seq[String]) =
    val s = Finder(root).recursiveFindByType[Schema].head
    (s.keys.map(_.pathId.format), s.history.map(_.value))

  "schema keys in JSON" should {

    "serialize keys and history" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"keys\":[\"Tickets.StoredTicket.ticketId\"]")
      json must include("\"history\":[\"tickets\"]")
    }

    "round-trip and be a fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          shape(back) mustBe shape(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
