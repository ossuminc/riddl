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

/** `on quiescence <window>` must survive a BAST round trip in BOTH window forms.
  *
  * The window lives in a FIELD, written by `writeOnQuiescenceClause` as a tagged value before the
  * contents count (discriminator byte 7, `FORMAT_REVISION` 24), so the generic traversal that
  * writes `contents` never carries it. Misalignment surfaces at whatever comes NEXT, which is why
  * the second case counts the statements that follow.
  */
class QuiescenceBASTRoundTripTest extends AbstractValidatingTest {

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

  private val src =
    """domain D is {
      |  context C is {
      |    constant Grace: Duration = "30 minutes" with { briefly "k" }
      |    command Touch is { id: String } with { briefly "c" }
      |    entity Cart is {
      |      handler H is {
      |        on command Touch is { do "touch" }
      |        on quiescence "30 minutes" is { do "literal window" }
      |      } with { briefly "h" }
      |    } with { briefly "e" }
      |    repository R is {
      |      handler RH is {
      |        on quiescence Grace is { do "value window" }
      |      } with { briefly "h" }
      |    } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  "on quiescence" should {

    "survive a BAST round trip in both window forms" in { (td: TestData) =>
      val root = roundTrip(src, "quiescence-bast")
      val clauses = Finder(root).recursiveFindByType[OnQuiescenceClause]
      clauses.size mustBe 2
      clauses.map(_.window.format).sorted mustBe Seq("\"30 minutes\"", "Grace")
      clauses.exists(_.window.isInstanceOf[LiteralString]) mustBe true
      clauses.exists(_.window.isInstanceOf[ValueRef]) mustBe true
    }

    "not corrupt the nodes that follow the clauses" in { (td: TestData) =>
      val root = roundTrip(src, "quiescence-bast-followers")
      Finder(root).recursiveFindByType[DoStatement].size mustBe 3
    }
  }
}
