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

/** Task 3: `on init`/`on term` parameter lists must survive a BAST round trip.
  *
  * Parameters are written directly in `writeOnInitializationClause`/`writeOnTerminationClause`
  * (mirroring `writeMethod`'s `args`), not through the generic `Pass` traversal that writes
  * `contents` -- so this is the one surface the `LifecycleParametersTest` validation suite does
  * not exercise at all.
  */
class LifecycleParametersBASTRoundTripTest extends AbstractValidatingTest {

  /** parse -> BAST -> decode. Returns the decoded Module (the nebula the writer wraps a Root in). */
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
      |    record R is { total: Integer } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on init(total: Integer) is { do "start" }
      |          on term(oid: Id(entity Order), reason: String) is { do "end" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  "on init/on term parameters" should {

    "survive a BAST round trip" in { (td: TestData) =>
      val root = roundTrip(src, "lifecycle-params-bast")
      val oic = Finder(root)
        .recursiveFindByType[OnInitializationClause]
        .headOption
        .getOrElse(fail("the OnInitializationClause did not survive"))
      oic.parameters.map(a => a.name -> a.typeEx.format) mustBe Seq("total" -> "Integer")

      val otc = Finder(root)
        .recursiveFindByType[OnTerminationClause]
        .headOption
        .getOrElse(fail("the OnTerminationClause did not survive"))
      otc.parameters.map(_.name) mustBe Seq("oid", "reason")
      otc.parameters.head.typeEx mustBe a[UniqueId]
    }

    "not corrupt the nodes that follow the clauses" in { (td: TestData) =>
      // Same failure mode Constant/Method hit sharing NODE_FIELD: misalignment surfaces at
      // whatever comes NEXT. Both on-clauses' bodies must survive intact.
      val root = roundTrip(src, "lifecycle-params-bast-followers")
      Finder(root).recursiveFindByType[PromptStatement].size mustBe 2
    }
  }
}
