/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Domain, Root, Module}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** BAST preserves OFFSETS exactly; line and column are derived from them by `At` against whatever
  * source is attached. Reported by synapify, which cannot move AnalysisPass off the Electron main
  * thread without re-parsing, because a diagnostic's position does not survive the round trip.
  *
  * The defect was never "positions are not stored". `BASTWriter.writeLocation` delta-encodes the
  * REAL source offset, and `DeepASTComparison` confirms offsets round-trip exactly. What went wrong
  * is that the reader attached a source which decoded those real offsets as if they were SYNTHETIC
  * ones (line L starting at L*10000), so every offset below 10000 landed on line 1 with the column
  * equal to the offset. It answered confidently and wrongly.
  */
class BASTLocationFidelityTest extends AnyWordSpec with Matchers {

  /** Line 2 col 3 is `domain`, and the entity/type below it are on known lines. */
  private val source: String =
    """// a comment on line 1
      |  domain D is {
      |    type T is String
      |  }
      |""".stripMargin

  /** The consumer supplies the very input it parsed with, which is the realistic case: a host that
    * just parsed the model already holds this.
    */
  private def input: RiddlParserInput = RiddlParserInput(source, "loc-fidelity")

  private def parsed: Root =
    TopLevelParser.parseInput(input, true) match
      case Right(root: Root) => root
      case Left(msgs)        => fail(msgs.format)

  private def bytes: Array[Byte] =
    val writerResult = Pass.runThesePasses(PassInput(parsed), Seq(BASTWriterPass.creator()))
    writerResult.outputOf[BASTOutput](BASTWriterPass.name).get.bytes

  private def domainOf(m: Module): Domain =
    Finder(m).recursiveFindByType[Domain].headOption match
      case Some(d) => d
      case None    => fail("no Domain in the reconstructed module")

  "BAST location fidelity" should {

    "preserve the offset exactly, with or without a source" in {
      val original = parsed.domains.head
      BASTReader.read(bytes) match
        case Right(m)   => domainOf(m).loc.offset mustBe original.loc.offset
        case Left(msgs) => fail(msgs.format)
    }

    "report position UNKNOWN rather than a wrong one when no source is supplied" in {
      // 0 is unrepresentable as a real position -- they are 1-based -- so it cannot be mistaken
      // for a real answer. Previously this returned line 1 and col = offset, which looks correct
      // enough that a Problems pane would point at it.
      BASTReader.read(bytes) match
        case Right(m) =>
          val loc = domainOf(m).loc
          loc.line mustBe 0
          loc.col mustBe 0
        case Left(msgs) => fail(msgs.format)
    }

    "recover the true line and column when the consumer supplies the source" in {
      val original = parsed.domains.head
      val rpi = input
      // Keyed by the origin the AST reports -- what a consumer holding the parsed tree would use.
      BASTReader.read(bytes, Map(original.loc.source.origin -> rpi)) match
        case Right(m) =>
          val loc = domainOf(m).loc
          loc.line mustBe original.loc.line
          loc.col mustBe original.loc.col
          loc.line mustBe 2 // `domain` really is on line 2
        case Left(msgs) => fail(msgs.format)
    }
  }
}
