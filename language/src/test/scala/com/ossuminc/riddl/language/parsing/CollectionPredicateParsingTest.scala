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

/** B5 (2026-09-23): `all of`/`any of`/`none of … as <e> where <p>`, the filter `xs as e where
  * p`, `count of xs` and `xs contains x`.
  *
  * The hazards pinned here are the ones the keywords create: `none` is also `empty`'s synonym,
  * `count` is a field name in 24 corpus records, `as` leads an ascription elsewhere, and
  * `foreach x in xs` must be untouched (membership is collection-first for exactly that family
  * of reason — `in` would have eaten B2's `store … in <table>`).
  */
abstract class CollectionPredicateParsingTest(using PlatformContext) extends AbstractParsingTest {

  private def parse(src: String, td: TestData) =
    TopLevelParser.parseInput(RiddlParserInput(src, td), true)

  private def wrap(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    record Item is { ready: Boolean, kind: String } with { briefly "i" }
       |    record Data is { items: C.Item*, zips: String* } with { briefly "d" }
       |    entity E is {
       |      state S of record C.Data
       |      handler H is { on init is { $stmt } } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def letValue(expr: String, td: TestData): Value =
    parse(wrap(s"let v = $expr"), td) match
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")
      case Right(root) =>
        Finder(root).recursiveFindByType[LetStatement].headOption.map(_.expression)
          .getOrElse(fail("no let"))

  "collection predicates" should {

    "parse all/any/none with an explicit element binding" in { (td: TestData) =>
      letValue("all of Data.items as item where item.ready", td) match
        case CollectionPredicate(_, CollectionQuantifier.All, _, e, _) => e.value mustBe "item"
        case other => fail(s"wrong shape: ${other.format}")
      letValue("""any of Data.items as i where i.kind == "drink"""", td) match
        case CollectionPredicate(_, CollectionQuantifier.Any, _, _, _: ComparisonExpression) => succeed
        case other => fail(s"wrong shape: ${other.format}")
      letValue("none of Data.items as i where i.ready", td) match
        case CollectionPredicate(_, CollectionQuantifier.None_, _, _, _) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "take the whole conjunction as the predicate" in { (td: TestData) =>
      letValue("""all of Data.items as i where i.ready and i.kind == "food"""", td) match
        case CollectionPredicate(_, _, _, _, _: LogicalExpression) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a bare `none` as the empty literal" in { (td: TestData) =>
      letValue("none", td) mustBe a[EmptyValue]
    }
  }

  "the filter, count and contains" should {

    "parse a filter, and count it" in { (td: TestData) =>
      letValue("""Data.items as i where i.kind == "drink"""", td) mustBe a[CollectionFilter]
      letValue("count of Data.items", td) match
        case CountValue(_, _: ValueRef) => succeed
        case other => fail(s"wrong shape: ${other.format}")
      letValue("""count of (Data.items as i where i.ready)""", td) match
        case CountValue(_, _: CollectionFilter) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse membership collection-first" in { (td: TestData) =>
      letValue("""Data.zips contains "94110"""", td) match
        case MembershipValue(_, coll, _: LiteralString) => coll.format mustBe "Data.zips"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "compare a count without parentheses" in { (td: TestData) =>
      letValue("count of Data.items > 0", td) match
        case ComparisonExpression(_, _, _: CountValue, _) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a bare `count` field reference alone" in { (td: TestData) =>
      val countSrc =
        """domain D is {
          |  context C is {
          |    record R is { count: Integer } with { briefly "r" }
          |    entity E is {
          |      state S of record C.R
          |      handler H is { on init is { let v = R.count } } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      parse(countSrc, td) match
        case Left(msgs) => fail(s"a field named 'count' must still parse:\n${msgs.format}")
        case Right(root) =>
          Finder(root).recursiveFindByType[LetStatement].head.expression mustBe a[ValueRef]
    }
  }

  "the surrounding grammar" should {

    "leave `foreach x in xs` untouched" in { (td: TestData) =>
      parse(wrap("""foreach i in field C.Data.items { do "each" }"""), td) match
        case Left(msgs) => fail(s"foreach broke:\n${msgs.format}")
        case Right(root) =>
          Finder(root).recursiveFindByType[ForeachStatement].size mustBe 1
    }

    "leave a prompt ascription's `as` untouched" in { (td: TestData) =>
      letValue("""prompt("a value") as Integer""", td) match
        case pv: PromptValue => pv.typeEx mustBe defined
        case other           => fail(s"wrong shape: ${other.format}")
    }
  }
}
