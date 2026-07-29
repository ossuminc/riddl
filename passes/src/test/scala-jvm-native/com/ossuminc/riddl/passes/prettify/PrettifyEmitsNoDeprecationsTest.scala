/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Prettify must emit the CANONICAL spelling of every construct, never a deprecated one.
  *
  * Prettify is how a model gets canonicalised, so if it emits a deprecated spelling then running it
  * over a clean model INTRODUCES deprecations — the opposite of its job. That is exactly what
  * happened when `state` gained its deprecation: `openState` emitted `is` before the record
  * reference for a state WITH A BODY (the body-less branch, which was right, is a different code
  * path), so every bodied state came back deprecated.
  *
  * Note what could NOT catch this. The prettify-agreement check in `Root2JsonFixturesTest` compares
  * prettify(root0) against prettify(root1) — both sides emit the same wrong text, so they agree. A
  * round-trip test that only asserts the model re-parses does not catch it either, because a
  * deprecation is a warning, not an error. The assertion has to be on the MESSAGES.
  */
class PrettifyEmitsNoDeprecationsTest extends AbstractValidatingTest {

  private def parseWithMessages(src: String, origin: String): (Root, Seq[?]) =
    TopLevelParser.parseInputWithMessages(RiddlParserInput(src, origin)) match
      case Right((root, msgs)) => (root, msgs.filter(_.isDeprecation))
      case Left(msgs)          => fail(s"parse of $origin failed:\n${msgs.format}")

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

  /** Every state spelling: bodied and body-less, initial and not. The bodied form is the one that
    * regressed, and the body-less one is the branch that was already correct — both are here so a
    * fix cannot trade one for the other.
    */
  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    entity Ent is {
      |      record Data is { x: Integer }
      |      state Bodied of record Dom.Ctx.Ent.Data is {
      |        handler H is { on other is { do "x" } }
      |      }
      |      initial state AlsoBodied of record Dom.Ctx.Ent.Data is {
      |        handler H2 is { on other is { do "y" } }
      |      }
      |      state Bodyless of record Dom.Ctx.Ent.Data with { briefly "no body" }
      |    }
      |  }
      |}
      |""".stripMargin

  "prettify" should {

    "emit no deprecated syntax for a model that has none" in { (td: TestData) =>
      val (root, before) = parseWithMessages(src, "source.riddl")
      withClue(s"the SOURCE must be clean for this test to mean anything: $before") {
        before mustBe empty
      }

      val prettified = prettify(root)
      val (_, after) = parseWithMessages(prettified, "prettified.riddl")

      withClue(s"prettify introduced deprecations:\n$prettified\n$after") {
        after mustBe empty
      }
    }

    "introduce a state's record reference with `of`, bodied or not" in { (td: TestData) =>
      val (root, _) = parseWithMessages(src, "source.riddl")
      val prettified = prettify(root)
      prettified must include("state Bodied of record")
      prettified must include("state AlsoBodied of record")
      prettified must include("state Bodyless of record")
      // ...and `is` still introduces the BODY, which is the whole point of the distinction.
      prettified must include("of record Dom.Ctx.Ent.Data is {")
    }
  }
}
