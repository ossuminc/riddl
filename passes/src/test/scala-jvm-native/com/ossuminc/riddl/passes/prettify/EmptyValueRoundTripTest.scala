/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** RIDDL's reflectivity mandate for the `empty` value: parse -> prettify -> re-parse.
  *
  * Two things this pins that nothing else would. **`none` CONVERGES to `empty`** — they are one
  * node with no spelling flag, so prettify has one spelling to emit, the same precedent `!` -> `not`
  * set. And **the ascription survives**, routed through `emitTypeExpression` rather than a narrower
  * `.format` copy, which is the mistake `PromptValue.ascriptionFormat` made.
  */
class EmptyValueRoundTripTest extends AbstractValidatingTest {

  private def src(stmt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Cleared is { why: String(1,20) } with { briefly "t" }
       |    type Notes is String(1,20)? with { briefly "n" }
       |    record Data is { note: Notes } with { briefly "d" }
       |    entity Ent is {
       |      state S of record Ctx.Data is {
       |        handler H is {
       |          on event Ctx.Cleared is {
       |            $stmt
       |          }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
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
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def emptyIn(root: Root): EmptyValue =
    Finder(root)
      .recursiveFindByType[EmptyValue]
      .headOption
      .getOrElse(fail("no EmptyValue found"))

  "a bare `empty`" should {
    "round-trip through prettify" in { (td: TestData) =>
      val original = parse(src("set field Data.note to empty"), "src")
      emptyIn(original).typeEx mustBe None

      val pretty = prettify(original)
      pretty must include("to empty")

      val regen = parse(pretty, "regen")
      emptyIn(regen).typeEx mustBe None
    }
  }

  "`none`" should {
    "converge to `empty`, because they are ONE node with no spelling flag" in { (td: TestData) =>
      val original = parse(src("set field Data.note to none"), "src")
      emptyIn(original).typeEx mustBe None

      val pretty = prettify(original)
      pretty must include("to empty")
      pretty must not include "to none"

      // And the converged text re-parses to the same node -- convergence is only safe because the
      // two spellings were never distinguishable in the AST to begin with.
      val regen = parse(pretty, "regen")
      emptyIn(regen) mustBe a[EmptyValue]
      emptyIn(regen).typeEx mustBe None
    }
  }

  "an ascribed `empty`" should {
    "keep its type through prettify" in { (td: TestData) =>
      val original = parse(src("set field Data.note to empty String(1,20)?"), "src")
      emptyIn(original).typeEx must not be empty

      val pretty = prettify(original)
      pretty must include("empty String(1,20)?")

      val regen = parse(pretty, "regen")
      emptyIn(regen).typeEx.map(_.format) mustBe emptyIn(original).typeEx.map(_.format)
    }
  }
}
