/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.*

/** A path identifier may descend INTO a Function to reach a definition nested in it.
  *
  * Until 2.0 any such path CRASHED the resolver. `findMatchingCandidate`'s Function arm did
  * `function.input.collect{…}.asInstanceOf[Definitions]`, but `Function.input`/`output` are
  * `Option[TypeRef | Aggregation]`, so it cast an `Option` to a `Seq[Definition]`. It threw
  * reliably, and BEFORE any name comparison -- which is why a nonexistent target failed identically
  * to a real one. Worse, it surfaced as a bare `[severe] empty(1:1->1):` with no text, no source
  * line and no location, so ossum.tech spent about half an hour bisecting a model by hand to find
  * it (task file 2026-08-08-empty-severe-on-dotted-path-through-function.md).
  *
  * Resolving is the intended behaviour (Reid, 2026-08-07), consistent with the general rule that
  * each path element is a child of the one before, and with what the arm already intended -- it
  * ends with `function.contents.directDefinitions`, which exists only to make nested definitions
  * reachable.
  *
  * But a nested function is the enclosing function's PRIVATE IMPLEMENTATION, so calling one from
  * OUTSIDE draws a StyleWarning. Calling your own helper does not: that is what nesting is for.
  */
class PathThroughFunctionTest extends AbstractValidatingTest {

  /** Parse and validate `src`, with style warnings explicitly ON.
    *
    * The pinning is load-bearing, not defensive. The private-nested-function message is a
    * StyleWarning, and `Messages.Accumulator` DROPS those unless `showStyleWarnings` is set.
    * `pc.options` is GLOBAL mutable state that other suites change via `withOptions`, so without
    * this the suite passed in isolation and failed inside the full run -- observed here on NATIVE
    * only, where a different suite ordering had left the flag off, while JVM stayed green. The same
    * trap is documented at `PortletOptionTest.scala:22`.
    *
    * It also removes a VACUOUS-PASS mode: with all warning categories suppressed the message list
    * came back empty, and every `mustBe empty` assertion below would have passed while checking
    * nothing at all.
    */
  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def hardFailures(src: String, origin: String): Seq[String] =
    diagnostics(src, origin).justErrors.map(_.message)

  private val privateNag = "private to it"

  /** Case B's shape, parameterised on the enclosing function's requires/returns form. */
  private def nestedModel(clauses: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    record TaxIn is { subtotal is Natural }
       |    function Tax is {
       |      $clauses
       |      function Compute is { requires TaxIn returns TaxIn ??? }
       |    }
       |    function Caller is {
       |      requires TaxIn returns TaxIn
       |      return call function Tax.Compute(subtotal)
       |    }
       |  }
       |}
       |""".stripMargin

  "a path descending into a Function" should {

    // Acceptance criterion: all three requires/returns shapes. The cast blew up on ALL of them --
    // `None` for the TypeRef and absent forms, `Some` for the inline Aggregation -- so each is
    // covered rather than assuming one stands for the rest.
    "resolve when the enclosing function uses a TypeRef requires/returns" in { (td: TestData) =>
      hardFailures(nestedModel("requires TaxIn returns TaxIn"), td.name) mustBe empty
    }

    "resolve when the enclosing function has NO requires/returns" in { (td: TestData) =>
      hardFailures(nestedModel(""), td.name) mustBe empty
    }

    "resolve when the enclosing function uses the deprecated inline aggregation" in {
      (td: TestData) =>
        hardFailures(
          nestedModel("requires { amount: Integer } returns { total: Integer }"),
          td.name
        ) mustBe empty
    }

    "report a real diagnostic when the nested target does not exist" in { (td: TestData) =>
      // Case C. The crash fired before any name comparison, so a missing target produced the
      // SAME empty severe as a present one. It must now name what could not be found.
      val errs = hardFailures(
        nestedModel("requires TaxIn returns TaxIn").replace("Tax.Compute", "Tax.Nonexistent"),
        td.name
      ).mkString("\n")
      errs must include("Nonexistent")
      errs must include("not resolved")
    }

    "keep a path into a sibling CONTEXT working (regression guard)" in { (td: TestData) =>
      // Case A -- the control that proved dotted-path resolution worked at all. It must not
      // become collateral damage of the Function fix.
      val src =
        """domain Dom is {
          |  context Tax is {
          |    record TaxIn is { subtotal is Natural }
          |    function Compute is { requires TaxIn returns TaxIn ??? }
          |  }
          |  context Ctx is {
          |    function Caller is {
          |      requires Tax.TaxIn returns Tax.TaxIn
          |      return call function Tax.Compute(subtotal)
          |    }
          |  }
          |}
          |""".stripMargin
      hardFailures(src, td.name) mustBe empty
    }
  }

  "a nested function's privacy" should {

    "draw a style warning when called from OUTSIDE the enclosing function" in { (td: TestData) =>
      val msgs = diagnostics(nestedModel("requires TaxIn returns TaxIn"), td.name)
        .map(_.message)
        .mkString("\n")
      msgs must include(privateNag)
      msgs must include("Compute")
      msgs must include("Tax")
    }

    "NOT warn when the enclosing function calls its own nested helper" in { (td: TestData) =>
      // The whole point of nesting. Warning here would make the feature unusable, so this is the
      // case that keeps the rule honest rather than merely loud.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    record TaxIn is { subtotal is Natural }
          |    function Tax is {
          |      requires TaxIn returns TaxIn
          |      function Compute is { requires TaxIn returns TaxIn ??? }
          |      return call function Compute(subtotal)
          |    }
          |  }
          |}
          |""".stripMargin
      diagnostics(src, td.name).map(_.message).mkString("\n") mustNot include(privateNag)
    }
  }
}
