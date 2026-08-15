/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.CommonOptions
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A literal's value is statically known where a reference's is not, so literals are held to a
  * STRICTER standard than the surrounding assignment rules.
  *
  * `NumericType.isAssignmentCompatible` deliberately lets ANY numeric accept any other, and that
  * stays true for references. The last case pins that from the loose side: if someone "tidies up"
  * by tightening `isAssignmentCompatible` itself, this suite goes red instead of silently
  * changing behaviour far beyond literals.
  */
class NumericLiteralConformanceTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end diagnostics

  private def constantModel(decl: String): String =
    s"""domain D is {
       |  context C is {
       |    $decl
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def errorsFor(decl: String, origin: String): Messages =
    diagnostics(constantModel(decl), origin).justErrors

  "an integer literal" should {
    "be accepted by Natural when positive" in { (td: TestData) =>
      errorsFor("constant N: Natural = 10", "nat-ok") mustBe empty
    }

    "be rejected by Natural when zero" in { (td: TestData) =>
      val errs = errorsFor("constant N: Natural = 0", "nat-zero")
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("Natural")) mustBe true
      }
    }

    "be rejected by Natural when negative" in { (td: TestData) =>
      errorsFor("constant N: Natural = -1", "nat-neg") must not be empty
    }

    "be accepted by Whole when zero" in { (td: TestData) =>
      errorsFor("constant W: Whole = 0", "whole-zero") mustBe empty
    }

    "be rejected by Whole when negative" in { (td: TestData) =>
      val errs = errorsFor("constant W: Whole = -1", "whole-neg")
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("Whole")) mustBe true
      }
    }

    "be accepted by Integer when negative" in { (td: TestData) =>
      errorsFor("constant I: Integer = -1", "int-neg") mustBe empty
    }

    "be accepted by Real — an integer is a fine real" in { (td: TestData) =>
      errorsFor("constant R: Real = 5", "real-int") mustBe empty
    }

    // Regression: the parser accepts unbounded digit runs, so a literal wider than a Long is legal
    // syntax. `asLong` (`text.toLong`) throws `NumberFormatException` on overflow -- inside a match
    // guard, which surfaces as `[severe] Exception Thrown` with no line number instead of a
    // diagnostic. The range check must compare via `asBigDecimal`, which has no such bound, so a
    // 20-digit literal is a clean Error (Whole/Natural both admit it -- it is positive) rather than
    // a thrown exception aborting validation.
    "not overflow on a 20-digit literal (Natural)" in { (td: TestData) =>
      errorsFor("constant N: Natural = 99999999999999999999", "nat-overflow") mustBe empty
    }

    "not overflow on a 20-digit literal (Whole)" in { (td: TestData) =>
      errorsFor("constant W: Whole = 99999999999999999999", "whole-overflow") mustBe empty
    }

    "not overflow on a negative 20-digit literal, and report it cleanly (Whole)" in {
      (td: TestData) =>
        val errs = errorsFor("constant W: Whole = -99999999999999999999", "whole-neg-overflow")
        withClue(errs.map(_.message).mkString("\n")) {
          errs.exists(_.message.contains("Whole")) mustBe true
        }
    }
  }

  "a real literal" should {
    "be rejected by an integer type" in { (td: TestData) =>
      val errs = errorsFor("constant N: Natural = 1.5", "nat-frac")
      withClue(errs.map(_.message).mkString("\n")) {
        // The fractional arm must win over the range arm: reporting "not greater than zero" for
        // 1.5 would be true and useless.
        errs.exists(m => m.message.contains("whole number")) mustBe true
      }
    }

    "be accepted by Real" in { (td: TestData) =>
      errorsFor("constant R: Real = 1.5", "real-frac") mustBe empty
    }

    "be accepted by Real in scientific notation" in { (td: TestData) =>
      errorsFor("constant R: Real = 1.5e-3", "real-exp") mustBe empty
    }
  }

  "a reference, unlike a literal" should {
    "stay loosely compatible — a Real-typed field still assigns to a Natural" in {
      (td: TestData) =>
        // `let x: Natural = …` cannot be written directly: `LetStatement.typeRef` is a `TypeRef`
        // (a path reference, resolved through the symbol table), and a bare predefined-type
        // keyword like `Natural` is recognized only where the full TypeExpression grammar applies
        // (a field type, a `constant`'s declared type, …) — it is never entered into the symbol
        // table, so a `TypeRef` naming it bare is "not resolved", regardless of this task's check.
        // A user-declared alias IS a real `Type` definition and resolves normally, which is enough
        // to exercise what this case is actually pinning: that a Real-typed reference assigns into
        // a Natural-aliased local without complaint.
        val src =
          """domain D is {
            |  context C is {
            |    type Nat is Natural with { briefly "nat" }
            |    record St is { rate: Real, note: String } with { briefly "st" }
            |    command Cmd is { why: String } with { briefly "cmd" }
            |    entity E is {
            |      state S of record St is {
            |        handler H is {
            |          on command Cmd { let x: Nat = rate }
            |        } with { briefly "h" }
            |      } with { briefly "s" }
            |    } with { briefly "e" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val errs = diagnostics(src, "ref-stays-loose").justErrors
        withClue(errs.map(_.message).mkString("\n")) {
          errs mustBe empty
        }
    }
  }
}
