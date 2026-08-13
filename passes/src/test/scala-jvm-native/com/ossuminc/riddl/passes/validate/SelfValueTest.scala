/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `self` denotes the currently executing processor instance.
  *
  * `self` carries what CANNOT be known statically -- that is the admission principle for its
  * fields, and it is why `id` (minted at runtime) and `version` (a composed coordinate) are in
  * while `isClustered` waits for the clusterability spec.
  *
  * The type is a SYNTHESIZED Aggregation rather than a bespoke node, which is what makes
  * `let me = self` + `me.id` work through the ordinary ValueRef walk. A test for that indirect
  * form is therefore worth more than a test for `self.id`: it proves the type is real, not that
  * one parser arm fires.
  */
class SelfValueTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def inEntity(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "c" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is {
       |          on command Go { $body }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "self" should {

    "type self.id as Id of the enclosing processor" in { (td: TestData) =>
      diagnostics(inEntity("""let mine = self.id"""), "self-id").justErrors mustBe empty
    }

    "type self.version" in { (td: TestData) =>
      diagnostics(inEntity("""let v = self.version"""), "self-version").justErrors mustBe empty
    }

    "support `let me = self` then `me.id`" in { (td: TestData) =>
      // THE case that proves self's type is a real Aggregation rather than a parser trick.
      // If self were special-cased at the `self.<field>` syntax only, this would fail to resolve.
      diagnostics(inEntity("""let me = self
                             |            let mine = me.id""".stripMargin), "self-let")
        .justErrors mustBe empty
    }

    "REJECT an unknown field" in { (td: TestData) =>
      // The field set is CLOSED. A fall-through would silently accept self.anything.
      val text = diagnostics(inEntity("""let x = self.nonesuch"""), "self-bad-field")
        .justErrors.map(_.message).mkString("\n")
      text must include("nonesuch")
      text must include("id")
      text must include("version")
    }

    "REJECT self outside a processor" in { (td: TestData) =>
      // A Function is pure (A26) and carries no processor instance -- not even when nested inside
      // a Context, which is why this asserts `self` does NOT leak in from the enclosing Processor.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    function F is {
          |      requires { a: Integer }
          |      returns { b: Integer }
          |      return self.id
          |    } with { briefly "f" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "self-no-processor").justErrors.map(_.message).mkString("\n")
      text must include("self")
    }

    "round-trip `let me = self` and `let mine = self.id` through prettify" in { (td: TestData) =>
      val src = inEntity("""let me = self
                           |            let mine = self.id""".stripMargin)
      val pretty = prettify(parse(src, "self-roundtrip-src"))
      pretty must include("let me = self")
      pretty must include("let mine = self.id")
      // And the re-parse must still validate clean -- text matching alone would miss a form that
      // prettifies right but no longer parses.
      diagnostics(pretty, "self-roundtrip-regen").justErrors mustBe empty
    }
  }

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
}
