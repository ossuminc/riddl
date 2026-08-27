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

/** The separator after `is` is exactly ONE space, everywhere (riddl-models, 2026-08-25).
  *
  * Prettify emitted `command X is  {` with TWO spaces for all seven aggregate-use-case keywords
  * (`command`, `event`, `query`, `result`, `record`, `graph`, `table`) while plain `type X is {`
  * and every container got one, and `term N is  "text" ` got two plus a trailing space.
  *
  * **Whitespace is load-bearing here, not cosmetic.** riddl-models diffs its 190 checked-in `.bast`
  * files against source BYTE FOR BYTE, and that comparison can only be exact while the corpus is
  * precisely what prettify emits. 188 of 188 models had drifted, dominated by this shape. Reid:
  * *"Byte non-identical, especially with mere white space changes, is a source of frustration at
  * best and a source of errors at worst."*
  *
  * **Why idempotence could not catch it, and this can.** `prettify(prettify(x)) == prettify(x)`
  * held throughout: whatever the emitter does is canonical BY CONSTRUCTION, so the property is a
  * tautology with respect to its own spacing. This asserts the separator against a fixed
  * expectation instead, which is the only kind of check that can contradict the emitter. Same shape
  * as the stream defect of rc.24-3, where nothing asserted which stream a command wrote to.
  */
class IsSeparatorSpacingRoundTripTest extends AbstractValidatingTest {

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

  /** Every shape that reaches the `is` separator: containers, plain `type`, all seven
    * aggregate-use-case keywords, and `term`.
    */
  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    type Plain is { p: String(1,9) }
      |    record Rec is { r: String(1,9) }
      |    graph Grf is { g: String(1,9) }
      |    table Tbl is { t: String(1,9) }
      |    result Res is { s: String(1,9) }
      |    event Evt is { e: String(1,9) }
      |    query Qry replies result Ctx.Res is { q: String(1,9) }
      |    command Cmd yields event Ctx.Evt is { c: String(1,9) }
      |    entity Ent is { handler H is { on other is { do "x" } } }
      |  } with { term Expo is "the pass where finished plates are called" }
      |}
      |""".stripMargin

  "the `is` separator" should {

    "be a single space in EVERY declaration prettify emits" in { (_: TestData) =>
      val out = prettify(parse(src, "is-spacing"))
      val offenders = out.linesIterator.filter(_.contains("is  ")).toSeq
      withClue(s"lines with a doubled separator:\n${offenders.mkString("\n")}\n\nfull output:\n$out") {
        offenders mustBe empty
      }
    }

    "leave no trailing whitespace on any line" in { (_: TestData) =>
      // `term N is "text" ` carried one. A trailing space is invisible in a terminal and very
      // visible in a byte-exact diff, which is exactly the comparison this has to survive.
      val out = prettify(parse(src, "is-spacing-trailing"))
      val offenders = out.linesIterator.filter(l => l.nonEmpty && l != l.stripTrailing()).toSeq
      withClue(s"lines with trailing whitespace:\n${offenders.map(l => s"[$l]").mkString("\n")}") {
        offenders mustBe empty
      }
    }

    "emit each aggregate-use-case keyword with one space before its brace" in { (_: TestData) =>
      // Named individually so a regression names the keyword that broke rather than a count.
      val out = prettify(parse(src, "is-spacing-kinds"))
      for kw <- Seq("record", "graph", "table", "result", "event", "query", "command", "type") do
        withClue(s"'$kw' declaration in:\n$out") {
          out must include regex s"$kw \\w+[^\\n]* is \\{"
        }
    }

    "emit `term N is \"text\"` with one space and no padding" in { (_: TestData) =>
      val out = prettify(parse(src, "is-spacing-term"))
      withClue(out) {
        out must include("""term Expo is "the pass where finished plates are called"""")
      }
    }

    "still re-parse, so the spacing change did not break the grammar" in { (_: TestData) =>
      // A string assertion alone would pass against output riddlc itself rejects — the caution
      // TypeExpressionSpacingRoundTripTest records.
      val out = prettify(parse(src, "is-spacing-reparse"))
      parse(out, "is-spacing-reparsed")
      succeed
    }
  }
}
