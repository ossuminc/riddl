/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** Numeric literals and the widened `Constant` across the wire format, at revision 18.
  *
  * **A BAST error names where the reader DERAILED, never what derailed it.** So the decisive case
  * is not the literal itself but the one with definitions AFTER it: a tag whose payload the reader
  * mis-sizes leaves the stream misaligned, and the damage surfaces on some later, innocent node.
  */
class NumericLiteralBASTRoundTripTest extends AbstractValidatingTest {

  /** parse -> BAST -> decode. Returns the decoded tree, which is a Module (the nebula the writer
    * wraps a Root in), not a Root.
    */
  private def roundTrip(src: String, origin: String): Module =
    val root = TopLevelParser.parseInput(RiddlParserInput(src, origin), true) match
      case Right(r)   => r
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    BASTReader(bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")

  private def constantModel(decl: String): String =
    s"""domain D is {
       |  context C is {
       |    $decl
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def constantValueOf(m: Module, name: String): ConstantValue =
    Finder(m)
      .recursiveFindByType[Constant]
      .find(_.id.value == name)
      .map(_.value)
      .getOrElse(fail(s"constant '$name' not found in the decoded tree"))

  "a numeric literal" should {

    "survive with its text unchanged, in every form" in { (td: TestData) =>
      for form <- Seq("5", "-1", "+3", "007", "1.50", "-0.25", "1e3", "1.5e-3", "2E+8") do
        val decoded = roundTrip(constantModel(s"constant K: Real = $form"), s"bast-$form")
        constantValueOf(decoded, "K") match
          case nl: NumericLiteral => withClue(s"form $form: ") { nl.text mustBe form }
          case other              => fail(s"form $form decoded as ${other.getClass.getSimpleName}")
      end for
    }

    "survive as a comparison operand" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    record St is { count: Integer, note: String } with { briefly "st" }
          |    command Cmd is { why: String } with { briefly "cmd" }
          |    entity E is {
          |      state S of record St is {
          |        handler H is {
          |          on command Cmd { when count > 5 then do "big" end }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val decoded = roundTrip(src, "bast-comparand")
      // NOTE: `Finder.recursiveFindByType` descends into a `WhenStatement`'s then/else statement
      // lists but not into its `condition` field (see `Finder.scala`'s `consider`), so a
      // `ComparisonExpression` nested in a condition is invisible to a direct
      // `recursiveFindByType[ComparisonExpression]` lookup -- a pre-existing gap, not a BAST
      // defect. Reach the condition through the WhenStatement instead, as JsonRoundTripTest does.
      val whens = Finder(decoded).recursiveFindByType[WhenStatement]
      whens must not be empty
      whens.head.condition match
        case ce: ComparisonExpression =>
          ce.right match
            case nl: NumericLiteral => nl.text mustBe "5"
            case other              => fail(s"comparand decoded as ${other.getClass.getSimpleName}")
        case other => fail(s"when-condition decoded as ${other.getClass.getSimpleName}")
    }
  }

  "a widened constant" should {

    "keep a numeric value" in { (td: TestData) =>
      constantValueOf(roundTrip(constantModel("constant K: Integer = 5"), "c-num"), "K") match
        case nl: NumericLiteral => nl.text mustBe "5"
        case other              => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a boolean value" in { (td: TestData) =>
      constantValueOf(roundTrip(constantModel("constant K: Boolean = true"), "c-bool"), "K") match
        case bl: BooleanLiteral => bl.value mustBe true
        case other              => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a prompt value" in { (td: TestData) =>
      val decl = """constant K: Real = prompt("the gravitational constant")"""
      constantValueOf(roundTrip(constantModel(decl), "c-prompt"), "K") match
        case pv: PromptValue => pv.text must include("gravitational")
        case other           => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a string value" in { (td: TestData) =>
      val decl = """constant K: String = "Fred""""
      constantValueOf(roundTrip(constantModel(decl), "c-str"), "K") match
        case ls: LiteralString => ls.s mustBe "Fred"
        case other             => fail(s"decoded as ${other.getClass.getSimpleName}")
    }
  }

  "the stream after a numeric literal" should {

    "stay aligned — later definitions decode intact" in { (td: TestData) =>
      // THE case that distinguishes a real fix from a plausible one. A mis-sized payload derails
      // the reader somewhere AFTER the literal, on a node that is entirely innocent.
      val src =
        """domain D is {
          |  context C is {
          |    constant K: Integer = 5 with { briefly "k" }
          |    type T is String with { briefly "t" }
          |    command Cmd is { why: String } with { briefly "cmd" }
          |    entity E is {
          |      handler H is { on command Cmd { do "work" } } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val decoded = roundTrip(src, "bast-alignment")
      Finder(decoded).recursiveFindByType[Type].map(_.id.value) must contain("T")
      Finder(decoded).recursiveFindByType[Entity].map(_.id.value) must contain("E")
      Finder(decoded).recursiveFindByType[Handler].map(_.id.value) must contain("H")
    }
  }
}
