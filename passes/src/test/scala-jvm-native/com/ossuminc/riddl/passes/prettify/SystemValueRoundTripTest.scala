/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*
import org.scalatest.TestData

/** `system.now` / `system.random` survive prettify (Reid, 2026-08-25).
  *
  * RIDDL is fully reflective: anything that parses must also be emitted. A new `Value` arm is only
  * half done until a parse -> prettify -> re-parse round trip preserves it.
  */
class SystemValueRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
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
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain D is {
      |  context C is {
      |    event Ticked is { at: TimeStamp  score: Real }
      |    record Reading is { at: TimeStamp  startedAt: TimeStamp  score: Real }
      |    entity Meter is {
      |      state Running of record C.Reading is { ??? }
      |      state Stopped of record C.Reading is { ??? }
      |      handler H is {
      |        on event C.Ticked is {
      |          set field Reading.at to system.now
      |          set field Reading.score to system.random
      |          when Reading.startedAt < system.now then
      |            do "started before now"
      |          end
      |          yield event C.Ticked(at = system.now, score = system.random)
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "system values" should {
    "survive prettify in every position" in { (_: TestData) =>
      val out = prettify(parse(src, "system-rt"))
      withClue(out) {
        // Three `system.now` (set, comparison, constructor arg) and two `system.random`.
        "system\\.now".r.findAllIn(out).size mustBe 3
        "system\\.random".r.findAllIn(out).size mustBe 2
      }
    }

    "re-parse, so the emitted text is real source" in { (_: TestData) =>
      // A string assertion alone can pass against output riddlc itself rejects — the caution
      // TypeExpressionSpacingRoundTripTest records.
      val out = prettify(parse(src, "system-rt-2"))
      parse(out, "system-rt-reparsed")
      succeed
    }

    "reach a fixed point" in { (_: TestData) =>
      val once = prettify(parse(src, "system-rt-3"))
      val twice = prettify(parse(once, "system-rt-4"))
      twice mustBe once
    }
  }
}
