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

/** B5 (2026-09-23): the collection predicate, filter, `count of` and `contains` on the prettify
  * and BAST surfaces (value tags 18-21, revision 30).
  */
class CollectionPredicateRoundTripTest extends AnyWordSpec with Matchers {

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
      |          let b = any of Data.items as i where i.kind == "drink"
      |          let c = none of Data.items as i where i.ready
      |          let d = Data.items as i where i.ready
      |          let e = count of Data.items
      |          let f = Data.zips contains "94110"
      |          let g = count of (Data.items as i where i.ready) > 0
      |        }
      |      } with { briefly "h" }
      |    } with { briefly "e" }
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
    Finder(root.contents).recursiveFindByType[LetStatement].map(_.expression.format)

  private val expected = Seq(
    "all of Data.items as item where item.ready",
    "any of Data.items as i where i.kind == \"drink\"",
    "none of Data.items as i where i.ready",
    "Data.items as i where i.ready",
    "count of Data.items",
    "Data.zips contains \"94110\"",
    "count of (Data.items as i where i.ready) > 0"
  )

  "the collection forms" should {

    "parse to the expected shapes (control)" in {
      val root = parse(src, "src")
      shapes(root) mustBe expected
      // TREE, not text: `count of (filter) > 0` must be a comparison whose LEFT is the count --
      // without the parentheses in `format` it re-parses as a filter whose predicate is `p > 0`,
      // which formats identically and is a different program.
      Finder(root.contents).recursiveFindByType[LetStatement].last.expression match
        case ComparisonExpression(_, _, CountValue(_, _: CollectionFilter), _) => succeed
        case other => fail(s"wrong tree for the counted filter: ${other.format}")
    }

    "PRETTIFY and re-parse to the same trees" in {
      val pretty = prettify(parse(src, "src"))
      val again = parse(pretty, "regen")
      shapes(again) mustBe expected
      again.contents.toString.nonEmpty mustBe true
      Finder(again.contents).recursiveFindByType[LetStatement].last.expression match
        case ComparisonExpression(_, _, CountValue(_, _: CollectionFilter), _) => succeed
        case other => fail(s"re-parse changed the tree: ${other.format}")
      // the quantifiers survive by KIND, not merely by text
      Finder(again.contents).recursiveFindByType[CollectionPredicate].map(_.quantifier) mustBe
        Seq(CollectionQuantifier.All, CollectionQuantifier.Any, CollectionQuantifier.None_)
    }

    "survive BAST at revision 30" in {
      val written = Pass
        .runThesePasses(PassInput(parse(src, "src")), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          shapes(back) mustBe expected
          Finder(back.contents).recursiveFindByType[CollectionPredicate].map(_.element.value) mustBe
            Seq("item", "i", "i")
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
