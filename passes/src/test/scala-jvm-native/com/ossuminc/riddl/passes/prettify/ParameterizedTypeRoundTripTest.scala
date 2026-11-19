/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** riddl-models, 2026-11-18: `on init(a: Currency(USD))` prettified to `on init(a: Currency)`,
  * which does not parse. Lifecycle parameters render through `TypeExpression.format`, and
  * `Currency` had no override, so it fell back to its bare kind; a record field survived only
  * because `RiddlFileEmitter` carries its own Currency arm. Probing every parameterized type found
  * a worse sibling: `ZonedDate.format` was a copy of `ZonedDateTime.format`, so EVERY `ZonedDate`
  * -- record fields included -- came back as a `ZonedDateTime`, an absent zone came back as an
  * invented `"UTC"`, and the zone was quoted, which the parser rejects.
  *
  * The comparison is on a SHAPE that never calls `format`, since `format` is what was wrong.
  */
class ParameterizedTypeRoundTripTest extends AbstractValidatingTest {

  private val types: Seq[(String, String)] = Seq(
    "cur" -> "Currency(USD)",
    "zd0" -> "ZonedDate()",
    "zd1" -> "ZonedDate(EST)",
    "zt0" -> "ZonedDateTime()",
    "zt1" -> "ZonedDateTime(UTC)",
    "url" -> """URL("http")""",
    "str" -> "String(1,5)",
    "dec" -> "Decimal(8,2)"
  )

  private val src: String =
    val fields = types.map((n, t) => s"$n: $t").mkString(", ")
    s"""domain D is {
       |  context C is {
       |    entity E is {
       |      record Rec is { $fields }
       |      state Sta of record Rec
       |      handler H is {
       |        on init($fields) is { do "start" }
       |        on other is { error "unexpected" }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  /** What a type expression IS, without asking its `format`. */
  private def shape(te: TypeExpression): String = te match
    case c: Currency      => s"Currency/${c.country}"
    case z: ZonedDate     => s"ZonedDate/${z.zone.map(_.s)}"
    case z: ZonedDateTime => s"ZonedDateTime/${z.zone.map(_.s)}"
    case u: URI           => s"URI/${u.scheme.map(_.s)}"
    case s: String_       => s"String/${s.min}/${s.max}"
    case d: Decimal       => s"Decimal/${d.whole}/${d.fractional}"
    case other            => s"unexpected ${other.getClass.getSimpleName}"

  private val expected: Seq[(String, String)] = Seq(
    "cur" -> "Currency/USD",
    "zd0" -> "ZonedDate/None",
    "zd1" -> "ZonedDate/Some(EST)",
    "zt0" -> "ZonedDateTime/None",
    "zt1" -> "ZonedDateTime/Some(UTC)",
    "url" -> "URI/Some(http)",
    "str" -> "String/Some(1)/Some(5)",
    "dec" -> "Decimal/8/2"
  )

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}\n$text")

  private def fieldShapes(root: Container[?]): Seq[(String, String)] =
    Finder(root)
      .recursiveFindByType[Type]
      .filter(_.id.value == "Rec")
      .flatMap(_.typEx match
        case a: AggregateTypeExpression => a.fields
        case other                      => fail(s"Rec is not an aggregate: $other"))
      .map(f => f.id.value -> shape(f.typeEx))

  private def paramShapes(root: Container[?]): Seq[(String, String)] =
    Finder(root)
      .recursiveFindByType[OnInitializationClause]
      .flatMap(_.parameters)
      .map(p => p.name -> shape(p.typeEx))

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  "parameterized types" should {

    "parse to the expected shapes (the baseline the round trips compare against)" in {
      (td: TestData) =>
        val root = parse(src, td.name)
        fieldShapes(root) mustBe expected
        paramShapes(root) mustBe expected
    }

    "survive a prettify round trip, as record fields AND as on-init parameters" in {
      (td: TestData) =>
        val pretty = prettify(parse(src, td.name))
        pretty must include("on init(cur: Currency(USD), zd0: ZonedDate()")
        val again = parse(pretty, "prettified")
        withClue(pretty) {
          fieldShapes(again) mustBe expected
          paramShapes(again) mustBe expected
        }
    }

    "render through format exactly as written" in { (td: TestData) =>
      val root = parse(src, td.name)
      val formatted =
        Finder(root).recursiveFindByType[OnInitializationClause].flatMap(_.parameters).map(_.typeEx.format)
      formatted mustBe types.map(_._2)
    }

    "survive a BAST round trip" in { (td: TestData) =>
      val root = parse(src, td.name)
      val bytes = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes
      val decoded = BASTReader(bytes).read() match
        case Right(m)   => m
        case Left(msgs) => fail(s"BAST round trip failed:\n${msgs.format}")
      fieldShapes(decoded) mustBe expected
      paramShapes(decoded) mustBe expected
    }
  }
}
