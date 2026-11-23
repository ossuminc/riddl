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

/** The cardinality range `T{n,m}` (Reid, 2026-11-23), which had an AST node, a BAST tag and a JSON
  * key but no source spelling, on the parse, prettify and BAST surfaces. Compares TREES.
  *
  * The BAST case is the one that matters most: until FORMAT_REVISION 39 a SpecificRange shared
  * TYPE_RANGE with RangeType, and the reader guessed which by peeking for a TYPE_NUMBER or
  * TYPE_STRING byte -- so a range over `Integer` or an alias decoded as a RangeType.
  */
class RangeCardinalityRoundTripTest extends AnyWordSpec with Matchers {

  /** (field type as written, canonical spelling, min, max) */
  private val cases: Seq[(String, String, Long, Long)] = Seq(
    ("Integer{1,3}", "Integer{1,3}", 1L, 3L),
    ("Integer{2,}", "Integer{2,}", 2L, Long.MaxValue),
    ("Integer{,5}", "Integer{,5}", 0L, 5L),
    ("Integer{0,5}", "Integer{,5}", 0L, 5L),
    ("Integer{3,3}", "Integer{3,3}", 3L, 3L),
    ("Integer{,}", "Integer{,}", 0L, Long.MaxValue),
    ("String{1,2}", "String{1,2}", 1L, 2L),
    // `format` writes an alias with its `type` keyword, as it does for `?`, `*` and `+`.
    ("C.Seat{2,8}", "type C.Seat{2,8}", 2L, 8L),
    ("C.Seat {1,4}", "type C.Seat{1,4}", 1L, 4L)
  )

  private val src: String =
    s"""domain D is {
       |  context C is {
       |    record Seat is { row: Integer } with { briefly "s" }
       |    type Span is range(1, 5) with { briefly "a RangeType beside the ranges" }
       |    record R is {
       |${cases.zipWithIndex.map { case ((t, _, _, _), i) => s"      f$i: $t," }.mkString("\n")}
       |      span: C.Span
       |    } with { briefly "r" }
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

  private def sig(x: Any): String = x match
    case _: At           => ""
    case s: String       => s"\"$s\""
    case it: Iterable[?] => it.map(sig).mkString("[", ",", "]")
    case o: Option[?]    => o.map(sig).map(s => s"Some($s)").getOrElse("None")
    case p: Product =>
      p.productPrefix + p.productIterator.map(sig).filter(_.nonEmpty).mkString("(", ",", ")")
    case other => other.toString

  private def fieldTypes(root: Container[?]): Seq[TypeExpression] =
    Finder(root.contents).recursiveFindByType[Type].flatMap(_.typEx match
      case a: AggregateTypeExpression => a.fields.map(_.typeEx)
      case other                      => Seq(other))

  private def trees(root: Container[?]): Seq[String] = fieldTypes(root).map(sig)

  "a cardinality range" should {

    "parse to a SpecificRange with the written bounds" in {
      val root = parse(src, "src")
      val ranges = fieldTypes(root).collect { case sr: SpecificRange => sr }
      ranges.map(r => (r.min, r.max)) mustBe cases.map(c => (c._3, c._4))
      fieldTypes(root).collect { case rt: RangeType => rt } must have size 1
    }

    "format in the canonical spelling, writing a bound only when it is not open" in {
      val root = parse(src, "src")
      fieldTypes(root).collect { case sr: SpecificRange => sr.format } mustBe cases.map(_._2)
    }

    "PRETTIFY and re-parse to the same trees, and be a fixed point" in {
      val original = parse(src, "src")
      val text1 = prettify(original)
      text1 must include("f3: Integer{,5}")
      val again = parse(text1, "regen")
      trees(again) mustBe trees(original)
      prettify(again) mustBe text1
    }

    "survive BAST to the same trees, beside a RangeType" in {
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

    "not swallow a following brace that is not a range" in {
      // A `{` after a type that does not continue as `{n,m}` must backtrack, not fail the type.
      val root = parse(
        """domain D is { context C is {
          |  type T is Integer with { briefly "t" }
          |} with { briefly "c" } } with { briefly "d" }
          |""".stripMargin,
        "brace"
      )
      fieldTypes(root).head mustBe an[Integer]
    }
  }
}
