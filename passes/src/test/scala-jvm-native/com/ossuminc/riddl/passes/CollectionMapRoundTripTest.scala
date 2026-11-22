/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The collection map (`each of … yielding`, value tag 30, revision 38) on the prettify and BAST
  * surfaces. Compares TREES, not text: a `format` that drops a needed parenthesis re-parses to the
  * same text and a different program.
  */
class CollectionMapRoundTripTest extends AnyWordSpec with Matchers {

  private val exprs: Seq[String] = Seq(
    "each of Data.lines as l yielding l.sku",
    "each of Data.lines as l where l.qty > 0 yielding l.sku",
    "each of Data.lines as l where l.qty > 0 and l.price < 10 yielding l.qty * l.price",
    "each of Data.lines as l yielding record C.Ticket(code = l.sku, n = l.qty)",
    // `yielding` must stop `empty`'s optional ascription
    "each of Data.lines as l where l.sku == empty yielding l.sku",
    // a filter whose predicate is an `otherwise` is parenthesized, as for `one of`
    "each of Data.lines as l where (l.maybe otherwise 0) > 0 yielding l.sku",
    // the projection is a full value: it absorbs an `otherwise`
    "each of Data.lines as l yielding l.maybe otherwise 0",
    // as an operand the map is parenthesized
    "count of (each of Data.lines as l yielding l.sku)",
    "(each of Data.lines as l yielding l.sku) == empty",
    "(each of Data.lines as l yielding l.sku) otherwise empty",
    "(each of Data.lines as l yielding l.sku) contains \"x\"",
    "each of (Data.lines as l where l.qty > 0) as m yielding m.sku",
    "each of (each of Data.lines as l yielding l.qty) as q yielding q + 1",
    // a projection is a full value, so a map there needs no parentheses
    "each of Data.lines as l yielding each of Data.lines as k yielding k.qty",
    "sum of (each of Data.lines as l yielding l.qty)",
    // a map's projection would absorb an `otherwise` after it, on either side of one
    "Data.lines otherwise (each of Data.lines as l yielding l)",
    "Data.lines otherwise (each of Data.lines as l yielding l) otherwise empty",
    "Data.lines otherwise (each of Data.lines as l yielding l otherwise empty)",
    "when Data.lines == empty then each of Data.lines as l yielding l else Data.lines end"
  )

  private val src: String =
    s"""domain D is {
       |  context C is {
       |    record Line is { qty: Integer, price: Integer, sku: String, maybe: Integer? }
       |      with { briefly "l" }
       |    record Ticket is { code: String, n: Integer } with { briefly "t" }
       |    record Data is { lines: C.Line* } with { briefly "d" }
       |    entity E is {
       |      state S of record C.Data
       |      handler H is {
       |        on init is {
       |${exprs.zipWithIndex.map((e, i) => s"          let v$i = $e").mkString("\n")}
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}\n$text")

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

  /** A node's structure with every location erased. */
  private def sig(x: Any): String = x match
    case _: At           => ""
    case s: String       => s"\"$s\""
    case it: Iterable[?] => it.map(sig).mkString("[", ",", "]")
    case o: Option[?]    => o.map(sig).map(s => s"Some($s)").getOrElse("None")
    case p: Product =>
      p.productPrefix + p.productIterator.map(sig).filter(_.nonEmpty).mkString("(", ",", ")")
    case other => other.toString

  private def trees(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(l => sig(l.expression))

  private def texts(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(_.expression.format)

  "the collection map" should {

    "parse to a CollectionMap and format as written (control)" in {
      val root = parse(src, "src")
      texts(root) mustBe exprs
      val lets = Finder(root.contents).recursiveFindByType[LetStatement]
      lets.head.expression mustBe a[CollectionMap]
      lets(1).expression.asInstanceOf[CollectionMap].filter mustBe defined
      lets.head.expression.asInstanceOf[CollectionMap].filter mustBe None
    }

    "PRETTIFY and re-parse to the same trees, and be a fixed point" in {
      val original = parse(src, "src")
      val text1 = prettify(original)
      val again = parse(text1, "regen")
      trees(again) mustBe trees(original)
      texts(again) mustBe exprs
      prettify(again) mustBe text1
    }

    "survive BAST to the same trees" in {
      val original = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back)  => trees(back) mustBe trees(original)
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }

    "reach the projection and filter through Finder" in {
      val root = parse(src, "src")
      // `l.qty * l.price` lives only in the third map's projection
      Finder(root.contents).recursiveFindByType[ArithmeticExpression] must not be empty
    }
  }
}
