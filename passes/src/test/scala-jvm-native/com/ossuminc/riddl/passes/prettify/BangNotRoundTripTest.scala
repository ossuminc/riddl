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

/** Reid's 2026-08-14 ruling: `not` and `!` are synonymous everywhere. Task 1 made both spellings
  * build the identical `NotExpression` node (pinned by `BangNotSynonymyTest`, parser-level); this
  * suite pins the PRETTIFY half (task 3 of the `2026-08-15-not-bang-synonymy` plan): the decision,
  * already made, is that prettify emits `not` and `!` CONVERGES to it — the same precedent as
  * `A | B` prettifying to `one of { A or B }` because "RIDDL is meant to stay readable by people
  * who are not computer scientists". The corpus agrees empirically: 597 `not` uses, zero `!`.
  *
  * Modeled on `NumericLiteralRoundTripTest`'s `PrettifyPass` creator-chain shape. Every case
  * asserts the emitted TEXT, not merely that the output re-parses -- `!x` re-parses perfectly well
  * after being mangled into something else, which is exactly what a re-parse-only test would miss.
  *
  * Three things per position (the six pairs from Task 1's table, same statement kinds: `when`,
  * `require`, `let`, parenthesised, doubled, applied to a comparison):
  *   1. Source written with `not` round-trips byte-exact (the expected fragment is literally
  *      present in the emitted text).
  *   2. Source written with `!` prettifies to the IDENTICAL emitted text as the `not` source, and a
  *      SECOND prettify pass over that output is stable -- convergence, not oscillation.
  *   3. `a != b` survives untouched -- it is a comparison, not a negation, and gets its own case
  *      below since the corpus has zero `!=` uses to protect against a regression here.
  */
class BangNotRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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

  private def model(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    handler H is {
       |      on init {
       |        $stmt
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  /** One position from Task 1's table: a statement written both as `not` and as `!`, plus the
    * fragment the `not` emission must literally contain.
    */
  private case class Position(name: String, notStmt: String, bangStmt: String, expectedFragment: String)

  private val positions = Seq(
    Position(
      "when",
      "when not flag then\n  do \"boom\"\nend",
      "when !flag then\n  do \"boom\"\nend",
      "when not flag then"
    ),
    Position("require", "require not flag", "require !flag", "require not flag"),
    Position("let", "let x = not flag", "let x = !flag", "let x = not flag"),
    Position(
      "parenthesised",
      "when not (a and b) then\n  do \"boom\"\nend",
      "when !(a and b) then\n  do \"boom\"\nend",
      "when not (a and b) then"
    ),
    Position(
      "doubled",
      "when not not flag then\n  do \"boom\"\nend",
      "when !!flag then\n  do \"boom\"\nend",
      "when not not flag then"
    ),
    Position(
      "comparison",
      "when not a > b then\n  do \"boom\"\nend",
      "when !a > b then\n  do \"boom\"\nend",
      "when not a > b then"
    )
  )

  "prettify" should {
    for pos <- positions do
      s"emit `${pos.name}` as `not`, converge the `!` spelling to it, and stay stable on a " +
        "second pass" in { (td: TestData) =>
          val notEmitted = prettify(parse(model(pos.notStmt), s"${pos.name}-not-${td.name}"))
          withClue(s"emitted (not form) was:\n$notEmitted\n") {
            notEmitted must include(pos.expectedFragment)
          }

          val bangEmitted = prettify(parse(model(pos.bangStmt), s"${pos.name}-bang-${td.name}"))
          withClue(s"not-emitted:\n$notEmitted\nbang-emitted:\n$bangEmitted\n") {
            bangEmitted mustBe notEmitted
          }

          // Convergence, not oscillation: prettifying the ALREADY-prettified output a second time
          // must reproduce it exactly.
          val reEmitted = prettify(parse(notEmitted, s"${pos.name}-repass-${td.name}"))
          withClue(s"first pass:\n$notEmitted\nsecond pass:\n$reEmitted\n") {
            reEmitted mustBe notEmitted
          }
        }
    end for
  }

  "a `!=` comparison" should {
    "survive prettify untouched -- it is a comparison, not a negation" in { (td: TestData) =>
      val src = model("when a != b then\n  do \"boom\"\nend")
      val emitted = prettify(parse(src, s"ne-${td.name}"))
      withClue(s"emitted was:\n$emitted\n") {
        emitted must include("a != b")
        emitted must not include "not"
      }

      // Stability, same as the positive cases: a second pass changes nothing.
      val reEmitted = prettify(parse(emitted, s"ne-repass-${td.name}"))
      reEmitted mustBe emitted
    }
  }
}
