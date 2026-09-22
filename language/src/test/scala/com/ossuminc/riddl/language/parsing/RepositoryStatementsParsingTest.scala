/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** B2 (2026-09-22): `store`/`upsert`/`update`/`delete` are repository-only statements and
  * `query [one] <table> [where …]` is a value. The two parser hazards pinned here: the
  * keyword gate outside a repository, and `query Q(args)` staying a CONSTRUCTOR (the `NoCut`
  * plus the negative lookahead on `(`).
  */
abstract class RepositoryStatementsParsingTest(using PlatformContext) extends AbstractParsingTest {

  private def parse(src: String, td: TestData): Either[com.ossuminc.riddl.language.Messages.Messages, Root] =
    TopLevelParser.parseInput(RiddlParserInput(src, td), true)

  private def repo(body: String): String =
    s"""domain D is {
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
       |          $body
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def statements(src: String, td: TestData): Seq[Statement] =
    parse(src, td) match
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")
      case Right(root) => Finder(root).recursiveFindByType[Statement]

  "the storage statements" should {

    "parse store with a qualified table" in { (td: TestData) =>
      statements(repo("""store record C.Row(id = e.id, name = e.name) in S.rows"""), td).head match
        case StoreStatement(_, _: Constructor, t) =>
          t.schema.format mustBe "S"; t.table.value mustBe "rows"; t.isQualified mustBe true
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse upsert with an UNqualified table" in { (td: TestData) =>
      statements(repo("""upsert record C.Row(id = e.id, name = e.name) in rows"""), td).head match
        case UpsertStatement(_, _, t) =>
          t.isQualified mustBe false; t.table.value mustBe "rows"; t.format mustBe "rows"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse update with several assignments" in { (td: TestData) =>
      statements(repo("""update S.rows set name = e.name, id = e.id where id == e.id"""), td).head match
        case UpdateStatement(_, t, assigns, where) =>
          t.table.value mustBe "rows"
          assigns.map(_._1.value) mustBe Seq("name", "id")
          where mustBe a[ComparisonExpression]
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse delete" in { (td: TestData) =>
      statements(repo("""delete from S.rows where id == e.id"""), td).head match
        case DeleteStatement(_, t, _: ComparisonExpression) => t.table.value mustBe "rows"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "be refused outside a repository, at the keyword" in { (td: TestData) =>
      val src =
        """domain D is { context C is { handler H is {
          |  on init is { store record C.Row(id = "x") in S.rows }
          |} } }""".stripMargin
      parse(src, td) match
        case Left(msgs) => msgs.format must include("only allowed in a repository handler")
        case Right(_)   => fail("expected a parse failure outside a repository")
    }
  }

  "the query value" should {

    "parse with and without `one`, with and without `where`" in { (td: TestData) =>
      def q(text: String): QueryValue =
        statements(repo(s"let v = $text"), td).head match
          case LetStatement(_, _, _, qv: QueryValue) => qv
          case other => fail(s"wrong shape: ${other.format}")
      val a = q("query S.rows where id == e.id")
      a.one mustBe false; a.where mustBe defined; a.table.table.value mustBe "rows"
      val b = q("query one S.rows where id == e.id")
      b.one mustBe true
      val c = q("query rows")
      c.one mustBe false; c.where mustBe empty; c.table.isQualified mustBe false
      c.format mustBe "query rows"
    }

    "NOT swallow a query-message constructor -- `query Q(args)` stays a Constructor" in {
      (td: TestData) =>
        val src =
          """domain D is {
            |  context C is {
            |    query Ask replies result Ans is { id: String } with { briefly "q" }
            |    result Ans is { id: String } with { briefly "r" }
            |    entity E is {
            |      inlet In is query C.Ask with { briefly "i" }
            |      outlet Out is query C.Ask with { briefly "o" }
            |      handler H is {
            |        on init is { send query C.Ask(id = "x") to outlet E.Out }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        statements(src, td).collectFirst { case s: SendStatement => s.msg } match
          case Some(_: Constructor) => succeed
          case other                => fail(s"expected a Constructor operand, got $other")
    }
  }
}
