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

/** B7 (2026-09-21): `log <value>` on the parse, prettify and BAST surfaces (sub-kind 24,
  * revision 27). The operand kinds are asserted by class after the BAST read, so a value that
  * formats the same but came back as another node cannot hide.
  */
class LogStatementRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Ops is {
      |  context Printing is {
      |    command Print yields event Printed is { jobId: String, pages: Natural } with { briefly "c" }
      |    event Printed is { jobId: String, pages: Natural } with { briefly "e" }
      |    function PageCost is {
      |      requires { pages: Natural }
      |      returns { cost: Natural }
      |      log "costing"
      |      return pages * 2
      |    } with { briefly "f" }
      |    handler H is {
      |      on p: command Print is {
      |        log "printing job " + p.jobId
      |        log p.pages
      |        log p
      |      }
      |      on other as m is { log m }
      |    } with { briefly "h" }
      |  } with { briefly "p" option message_envelope("Riddl.Envelope") }
      |} with { briefly "o" }
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

  private def logs(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LogStatement].map(_.format)

  private val expected = Seq(
    "log \"costing\"",
    "log \"printing job \" + p.jobId",
    "log p.pages",
    "log p",
    "log m"
  )

  "log" should {

    "parse to five statements with the expected operands" in {
      val root = parse(src, "src")
      logs(root) mustBe expected
      Finder(root.contents).recursiveFindByType[LogStatement].map(_.value.getClass.getSimpleName) mustBe
        Seq("LiteralString", "ArithmeticExpression", "ValueRef", "ValueRef", "ValueRef")
    }

    "PRETTIFY to the same spelling and re-parse to the same statements" in {
      val root = parse(src, "src")
      val pretty = prettify(root)
      expected.foreach { text =>
        withClue(s"prettify lost '$text':\n$pretty") { pretty must include(text) }
      }
      logs(parse(pretty, "regen")) mustBe expected
    }

    "survive BAST at revision 27, operand kinds included" in {
      val root = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          logs(back) mustBe expected
          Finder(back.contents).recursiveFindByType[LogStatement].map(_.value.getClass.getSimpleName) mustBe
            Seq("LiteralString", "ArithmeticExpression", "ValueRef", "ValueRef", "ValueRef")
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
