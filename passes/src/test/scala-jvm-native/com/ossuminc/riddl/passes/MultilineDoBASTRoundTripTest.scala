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

/** Multi-line `do` / `prompt(...)` across BAST, the third serialization surface.
  *
  * `FORMAT_REVISION` moved to 23 for this: both now write a SEQUENCE where they wrote one string,
  * so a revision-22 file's bare string would be read as a count and everything after it would
  * derail. That is a misalignment rather than a clean failure, which is exactly why the revision
  * gate exists -- and why a BAST error names where the reader derailed, never what derailed it.
  */
class MultilineDoBASTRoundTripTest extends AbstractValidatingTest {

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

  private def model(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    handler H is {
       |      on init {
       |        $stmt
       |      }
       |    }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a do statement" should {

    "survive with every line intact" in { (td: TestData) =>
      val decoded = roundTrip(model("""do { "first" "second" "third" }"""), "bast-multi")
      val stmts = Finder(decoded).recursiveFindByType[DoStatement].toSeq
      stmts must have size 1
      stmts.head.what.map(_.s) mustBe Seq("first", "second", "third")
    }

    "survive the single-line form as exactly one line" in { (td: TestData) =>
      val decoded = roundTrip(model("""do "only one""""), "bast-single")
      Finder(decoded).recursiveFindByType[DoStatement].toSeq.head.what.map(_.s) mustBe
        Seq("only one")
    }
  }

  "a prompt value" should {

    "survive with every line and its ascription" in { (td: TestData) =>
      val decoded = roundTrip(model("""let x = prompt({ "one" "two" }) as Real"""), "bast-pv")
      val pv = Finder(decoded).recursiveFindByType[PromptValue].toSeq.head
      pv.prompt.map(_.s) mustBe Seq("one", "two")
      pv.typeEx mustBe defined
    }
  }
}
