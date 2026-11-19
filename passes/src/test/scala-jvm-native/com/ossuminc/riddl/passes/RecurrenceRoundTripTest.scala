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
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** Recurrence (2026-11-19) is reflective: a recurring `send` and an `on every` clause survive
  * parse -> prettify -> re-parse and a BAST round trip at the same place, with the TREE compared, not
  * just the text. The bare `starting` (no `at`) converges on `starting at`, the canonical spelling.
  * The `do` statements after each recurrence pin that nothing following it is misread.
  */
class RecurrenceRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    command Tick is { at: TimeStamp }
      |    constant Monthly: Duration = 30 days
      |    constant First: TimeStamp = "2026-12-01T00:00:00Z"
      |    entity E is {
      |      outlet O is command Tick
      |      handler H is {
      |        on init is {
      |          send command Tick(at = system.now) to outlet E.O every 30 days
      |          do "one"
      |          send command Tick(at = system.now) to outlet E.O every Monthly starting First
      |          do "two"
      |          send command Tick(at = system.now) to outlet E.O every "PT1H" starting at system.now + 1 hour
      |          do "three"
      |          send command Tick(at = system.now) to outlet E.O at First
      |          do "four"
      |        }
      |        on every 30 days is { do "five" }
      |        on every Monthly is { do "six" }
      |        on every "PT15M" is { do "seven" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin), true) match
      case Right(r)   => r
      case Left(msgs) => fail(s"parse of $origin failed:\n${msgs.format}\n$text")

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

  private def bast(root: Root): Module =
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    BASTReader(bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")

  /** The recurrence-bearing parts of a tree, rendered, plus the class of every period and start. */
  private def shape(root: Branch[?]): Seq[String] =
    val f = Finder(root)
    val sends = f.recursiveFindByType[SendStatement].map { s =>
      s"send ${s.at.map(v => s"at ${v.format}").getOrElse("")}${s.every.map(_.format).getOrElse("")} " +
        s.every.map(r => (r.period +: r.starting.toSeq).map(_.getClass.getSimpleName).mkString(",")).getOrElse("")
    }
    val clauses = f.recursiveFindByType[OnEveryClause].map { c =>
      s"${c.format} ${c.period.getClass.getSimpleName} [${c.statements.map(_.format).mkString(";")}]"
    }
    val dos = f.recursiveFindByType[DoStatement].map(_.text)
    sends ++ clauses ++ dos

  private val expected = Seq(
    "send every 30 days DurationLiteral",
    "send every Monthly starting at First ValueRef,ValueRef",
    "send every \"PT1H\" starting at system.now + 1 hour LiteralString,ArithmeticExpression",
    "send at First ",
    "on every 30 days DurationLiteral [\"five\"]",
    "on every Monthly ValueRef [\"six\"]",
    "on every \"PT15M\" LiteralString [\"seven\"]",
    "one",
    "two",
    "three",
    "four",
    "five",
    "six",
    "seven"
  )

  "recurrence" should {
    "parse to the expected tree" in { (td: TestData) =>
      shape(parse(src, "src")) mustBe expected
    }
    "survive prettify and re-parse with the same tree" in { (td: TestData) =>
      val text = prettify(parse(src, "src"))
      text must include("every Monthly starting at First")
      text must include("on every 30 days is")
      shape(parse(text, "prettified")) mustBe expected
    }
    "converge: prettifying the prettified text is a fixed point" in { (td: TestData) =>
      val once = prettify(parse(src, "src"))
      prettify(parse(once, "once")) mustBe once
    }
    "survive a BAST round trip with the same tree" in { (td: TestData) =>
      shape(bast(parse(src, "src"))) mustBe expected
    }
  }
}
