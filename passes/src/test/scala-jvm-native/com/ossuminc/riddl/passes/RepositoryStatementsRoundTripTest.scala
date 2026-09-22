/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B2 (2026-09-22): the four storage statements and the `query` value on the prettify and BAST
  * surfaces (statement sub-kinds 25-28, value tag 17, revision 29). Both table spellings —
  * qualified and bare — must survive: a bare table means "the repository's one schema", so
  * rewriting it would change what the model says.
  */
class RepositoryStatementsRoundTripTest extends AnyWordSpec with Matchers {

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
      |          update S.rows set name = e.name, id = e.id where id == e.id
      |          delete from rows where id == e.id
      |          let found = query one S.rows where id == e.id
      |          let all = query rows
      |        }
      |      } with { briefly "h" }
      |    } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
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
      .getOrElse(fail("no prettify output"))
      .state
      .filesAsString

  private def shapes(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[Statement].collect {
      case s: StoreStatement  => s.format
      case s: UpsertStatement => s.format
      case s: UpdateStatement => s.format
      case s: DeleteStatement => s.format
      case l: LetStatement    => l.expression.format
    }

  private val expected = Seq(
    "store record C.Row(id = e.id, name = e.name) in S.rows",
    "upsert record C.Row(id = e.id, name = e.name) in rows",
    "update S.rows set name = e.name, id = e.id where id == e.id",
    "delete from rows where id == e.id",
    "query one S.rows where id == e.id",
    "query rows"
  )

  "the storage statements and the query value" should {

    "parse to the expected shapes (control)" in { shapes(parse(src, "src")) mustBe expected }

    "PRETTIFY to the same spelling and re-parse equal" in {
      val pretty = prettify(parse(src, "src"))
      expected.foreach { text =>
        withClue(s"prettify lost '$text':\n$pretty") { pretty must include(text) }
      }
      shapes(parse(pretty, "regen")) mustBe expected
    }

    "survive BAST at revision 29, both table spellings intact" in {
      val written = Pass
        .runThesePasses(PassInput(parse(src, "src")), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          shapes(back) mustBe expected
          Finder(back.contents).recursiveFindByType[TableRef].map(_.isQualified) must contain(false)
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
