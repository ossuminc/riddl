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

/** B2 (2026-09-22) on the JSON surface: the four statement kinds, the `query` value, and the
  * table reference kept AS WRITTEN (a bare table means the repository's one schema).
  */
class RepositoryStatementsJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record Row is { id: String, name: String } with { briefly "r" }
      |    event E is { id: String, name: String } with { briefly "e" }
      |    repository R is {
      |      schema S is relational
      |        of rows as record C.Row
      |        key on field C.Row.id
      |      with { briefly "s" }
      |      handler H is {
      |        on e: event C.E is {
      |          store record C.Row(id = e.id, name = e.name) in S.rows
      |          upsert record C.Row(id = e.id, name = e.name) in rows
      |          update S.rows set name = e.name where id == e.id
      |          delete from rows where id == e.id
      |          let found = query one S.rows where id == e.id
      |        }
      |      } with { briefly "h" }
      |    } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def shapes(root: Root): Seq[String] =
    Finder(root).recursiveFindByType[Statement].collect {
      case s: StoreStatement  => s.format
      case s: UpsertStatement => s.format
      case s: UpdateStatement => s.format
      case s: DeleteStatement => s.format
      case l: LetStatement    => l.expression.format
    }

  "the storage statements in JSON" should {

    "serialize each kind and keep the table as written" in {
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      Seq("store", "upsert", "update", "delete").foreach { k =>
        withClue(k) { json must include(s""""kind":"$k"""") }
      }
      json must include("\"value\":\"query\"")
      json must include("\"table\":\"S.rows\"")
      json must include("\"table\":\"rows\"")
      json must include("\"one\":true")
    }

    "round-trip to the same statements and be a fixed point" in {
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
