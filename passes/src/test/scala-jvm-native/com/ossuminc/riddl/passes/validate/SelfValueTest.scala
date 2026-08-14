/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder, Messages}
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

    // C1 (code review, round 1): the legality/field-set check is wired into `validateValue`,
    // reached by `checkStatementScopes`'s recursion -- NOT into `validateStatement`, which only
    // sees statements the generic Pass dispatcher visits directly and does not descend into a
    // `WhenStatement`'s `thenStatements` (a FIELD, not `contents`). This is the regression case:
    // before the fix, a `self.nonesuch` nested inside a `when` block was silently accepted.
    "REJECT self.nonesuch nested inside a `when` block" in { (td: TestData) =>
      val text = diagnostics(
        inEntity("""when true then
                   |              let x = self.nonesuch
                   |            end""".stripMargin),
        "self-nested-when"
      ).justErrors.map(_.message).mkString("\n")
      text must include("nonesuch")
    }

    // C2 (code review, round 1): a Saga IS nestable inside a Context in the grammar
    // (`context_definition` includes `saga`), so `enclosingProcessorOf`'s `collectFirst` must
    // treat `Saga` as a TERMINATING case (like `Function`) rather than letting it fall through to
    // the enclosing Context -- otherwise `self` inside a saga step is silently typed as the
    // enclosing context's identity instead of being rejected. This also exercises the fact that
    // `checkStatementScopes` (and therefore `validateValue`) must be reached for saga-step
    // statements at all, which it previously was not.
    "REJECT self inside a saga step" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Go is { why: String } with { briefly "c" }
          |    command Undo is { why: String } with { briefly "u" }
          |    saga Saga1 is {
          |      requires { p: String }
          |      returns { r: String }
          |      step One is {
          |        let mine = self.id
          |      } reverted by {
          |        do "undo"
          |      } with { briefly "s" }
          |    } with { briefly "sg" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "self-saga-step").justErrors.map(_.message).mkString("\n")
      text must include("self")
    }

    // C3 (code review, round 1): cases 1/2 above assert only `justErrors mustBe empty`, which
    // passes identically whether or not `self`'s typing arm (`valueTypeExpr`'s `SelfValue` case)
    // exists at all -- `valueRefResolves`'s let-local branch never consults the field's type, so
    // nothing gates on it for a bare `let x = self.field`. This case IS load-bearing: `me.version`
    // used as a `when` condition routes through `checkWhenValueRef` -> `whenValueRefCategory` ->
    // `valueRefTypeExpr`, which (for a let-local path) falls back to
    // `valueTypeExpr(ls.expression, ...)` when the let has no declared type -- exactly the arm
    // under test. With the arm, `self.version`'s real type (`String_`) classifies as "string",
    // which is NOT boolean, so this must ERROR. Without the arm, the type is undetermined (None)
    // and `checkWhenValueRef` silently skips the check -- verified by commenting the arm out (see
    // task-2-report.md for the confirmation).
    "REJECT `me.version` as a boolean `when` condition -- proves self.version's type is a real " +
      "String_, not merely present" in { (td: TestData) =>
        val text = diagnostics(
          inEntity("""let me = self
                     |            when me.version then
                     |              error "unreachable"
                     |            end""".stripMargin),
          "self-version-boolean-check"
        ).justErrors.map(_.message).mkString("\n")
        text must include("must be a Boolean value")
        text must include("me.version")
      }
  }

  "SelfValue.aggregation" should {
    // C3 (code review, round 1): a direct unit assertion on the synthesized record itself,
    // independent of any validation-pass plumbing -- proves `id` really is `UniqueId` at the given
    // path and `version` really is `String_`, not merely that some type gets attached.
    "synthesize id: Id(<path>) and version: String" in { (td: TestData) =>
      val path = PathIdentifier(At.empty, Seq("Dom", "Ctx", "Order"))
      val agg = SelfValue.aggregation(path)
      agg.fields.map(_.id.value) mustBe Seq("id", "version")
      agg.fields.head.typeEx mustBe UniqueId(At.empty, path)
      agg.fields(1).typeEx mustBe String_(At.empty)
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
