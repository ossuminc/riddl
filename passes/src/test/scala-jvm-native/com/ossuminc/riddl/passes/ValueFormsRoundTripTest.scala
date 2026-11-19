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

/** BACKLOG [1.27] value forms on the prettify and BAST surfaces (value tags 22-26, revision 34).
  *
  * Compares TREES, not text -- the B5 lesson: a `format` that drops a needed parenthesis re-parses
  * to the same text and a different program. [[sig]] renders a value's full node structure with
  * every location erased, so two trees are equal exactly when they mean the same thing.
  */
class ValueFormsRoundTripTest extends AnyWordSpec with Matchers {

  /** One `let` per case; each is round-tripped and its tree compared. */
  private val exprs: Seq[String] = Seq(
    "minutes of Data.wait",
    "days of (Data.t2 - Data.t1)",
    "minutes of Data.wait + 1",
    "hours of Data.wait * 2",
    "Data.hours + Data.days"
  )

  private val src: String =
    s"""domain D is {
       |  context C is {
       |    record Line is { qty: Integer, price: Integer, sku: String } with { briefly "l" }
       |    record Data is { lines: C.Line*, wait: Duration, hours: Integer, days: Integer,
       |      t1: TimeStamp, t2: TimeStamp, maybe: C.Line? } with { briefly "d" }
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
    case _: At               => ""
    case s: String           => s"\"$s\""
    case it: Iterable[?]     => it.map(sig).mkString("[", ",", "]")
    case o: Option[?]        => o.map(sig).map(s => s"Some($s)").getOrElse("None")
    case p: Product          =>
      p.productPrefix + p.productIterator.map(sig).filter(_.nonEmpty).mkString("(", ",", ")")
    case other               => other.toString

  private def trees(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(l => sig(l.expression))

  private def texts(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(_.expression.format)

  "the value forms" should {

    "format as written (control)" in {
      texts(parse(src, "src")) mustBe exprs
    }

    "PRETTIFY and re-parse to the same trees" in {
      val original = parse(src, "src")
      val again = parse(prettify(original), "regen")
      trees(again) mustBe trees(original)
      texts(again) mustBe exprs
    }

    "survive BAST to the same trees" in {
      val original = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          trees(back) mustBe trees(original)
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
