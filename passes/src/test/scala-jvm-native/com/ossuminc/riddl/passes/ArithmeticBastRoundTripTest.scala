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
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B4 (2026-09-21), BAST revision 26: value tags 14 (`ArithmeticExpression`), 15
  * (`DurationLiteral`) and 16 (`ConstantRef` as a value), and a comparison whose operands are
  * written as VALUES rather than comparands. Each shape is asserted by its `format` after the
  * read, so a dropped operator, a lost unit or a mis-tagged operand cannot hide behind a
  * statement that merely survives.
  */
class ArithmeticBastRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    constant A: Natural = 10 with { briefly "a" }
      |    constant B: Natural = A + 1 with { briefly "b" }
      |    constant W: Duration = 1.50 hours with { briefly "w" }
      |    constant R: Natural = constant A * 2 with { briefly "r" }
      |    handler H is {
      |      on init {
      |        let v = (a + b) * c - d / 2
      |        let s = "x" + name
      |        let t = system.now + 30 days
      |        when a + b > c and not (d < 3 - 5) then { do "x" } end
      |      }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def shapes(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(_.expression.format) ++
      Finder(root.contents).recursiveFindByType[WhenStatement].map(_.condition.format) ++
      Finder(root.contents).recursiveFindByType[Constant].map(_.value.format)

  private val expected = Seq(
    "(a + b) * c - d / 2",
    "\"x\" + name",
    "system.now + 30 days",
    "a + b > c and not d < 3 - 5", // `not` binds looser than a comparison; no parens needed
    "10",
    "A + 1",
    "1.50 hours",
    "constant A * 2"
  )

  "B4 values in BAST" should {

    "parse to the expected shapes (control)" in {
      shapes(parse(src, "src")) mustBe expected
    }

    "survive a write and a read at revision 26" in {
      val root = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          shapes(back) mustBe expected
          // The node kinds, not just the text: a ConstantRef read back as a ValueRef would
          // format identically only by accident, so check the class.
          Finder(back.contents).recursiveFindByType[Constant].last.value match
            case ArithmeticExpression(_, _, _: ConstantRef, _) => succeed
            case other => fail(s"constant R's value read back as ${other.getClass.getSimpleName}")
          Finder(back.contents).recursiveFindByType[DurationLiteral].map(_.amount.text) mustBe
            Seq("1.50", "30")
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
