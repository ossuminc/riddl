/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A20 typed holes (`prompt("…") as T`): the ascription RESTATES the type its position already
  * supplies -- it never overrides it, mirroring A57's `on other as x: <envelope>` rule exactly. A
  * contradiction is an Error.
  *
  * The untyped-seam CompletenessWarning is deliberately CONSERVATIVE (Reid's ruling, 2026-08-15):
  * it fires ONLY on an unascribed `let x = prompt(…)` with no declared type -- the ONE position
  * the riddl-models corpus measurement showed was actually unascribed (0 of 288 uses). Every other
  * position (`when`, a constructor argument, `set`, …) stays silent, because "we have not wired
  * this position" is not the same fact as "the language cannot type it" -- widening the warning is
  * exactly the mistake that cost this project 1120 false external-context warnings and 854 hidden
  * `populates` warnings. The negative-control cases below exist so that mistake has a red test to
  * stop it, not just a comment.
  */
class TypedHoleValidationTest extends AbstractValidatingTest {

  // Mirrors NumericLiteralConformanceTest's shape: most cases here assert the ABSENCE of a
  // message, and a fixture that silently fails to parse (fastparse can recover from some
  // syntax errors and report an "Expected one of ..." message rather than aborting) would satisfy
  // that for free. Guard against it explicitly.
  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, showCompletenessWarnings = true)
    ) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end diagnostics

  private def errorsFor(src: String, origin: String): Messages = diagnostics(src, origin).justErrors

  private def completenessFor(src: String, origin: String): Messages =
    diagnostics(src, origin).filter(_.kind == Messages.CompletenessWarning)

  // `Score` is a declared Type (not a bare predefined keyword): a `LetStatement.typeRef` /
  // `SetStatement.field`'s type is a `TypeRef`, resolved through the symbol table, and a bare
  // predefined keyword like `Real` is never entered into it (see NumericLiteralConformanceTest's
  // "a reference, unlike a literal" case for the same constraint).
  private def entityModel(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type Score is Real
       |    command Go is { why: String }
       |    entity E is {
       |      handler H is { on command Go is { $stmt } }
       |    }
       |  }
       |}
       |""".stripMargin

  private def constantModel(decl: String): String =
    s"""domain D is {
       |  context C is {
       |    type Score is Real
       |    $decl
       |  }
       |}
       |""".stripMargin

  // `checkValueType`'s `set` arm resolves an expected type only when the field is declared with a
  // NAMED (aliased) type -- `balance: Score`, not `balance: Real` -- exactly as `let`/`constant`
  // require above.
  private def setModel(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type Score is Real
       |    record Acct is { balance: Score }
       |    command Go is { why: String }
       |    entity E is {
       |      state PS of record Acct is {
       |        handler H is { on command Go is { $stmt } }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a restated ascription" should {
    "be silent on a 'let' whose ascription matches its declared type" in { (td: TestData) =>
      errorsFor(
        entityModel("""let x: Score = prompt("d") as Score"""),
        "let-restate"
      ) mustBe empty
    }

    "be silent on a 'constant' whose ascription matches its declared type" in { (td: TestData) =>
      errorsFor(
        constantModel("""constant G: Real = prompt("gravity") as Real"""),
        "constant-restate"
      ) mustBe empty
    }

    "be silent on a 'constant' with no ascription -- the constant supplies the type" in {
      (td: TestData) =>
        errorsFor(
          constantModel("""constant G: Real = prompt("gravity")"""),
          "constant-no-ascription"
        ) mustBe empty
    }

    "be silent on a 'let' with no declared type but an ascribed hole" in { (td: TestData) =>
      // The claim under test is specifically that the SEAM WARNING did not fire -- the
      // `pv.typeEx.isEmpty` guard on `checkStatementScopes`'s `LetStatement` case makes it
      // provably so, but `completenessFor` pins that claim directly rather than the weaker
      // (already-covered-elsewhere) absence of Errors.
      completenessFor(
        entityModel("""let x = prompt("d") as Score"""),
        "let-ascribed-only"
      ).exists(_.message.contains("untyped")) mustBe false
    }

    "be silent on a 'set' whose ascription matches the field's type" in { (td: TestData) =>
      errorsFor(
        setModel("""set field Acct.balance to prompt("balance") as Score"""),
        "set-restate"
      ) mustBe empty
    }

    "be silent on a 'when' condition ascribed to Boolean" in { (td: TestData) =>
      // The design doc (2026-08-14-a20-typed-holes-design.md:94) lists this explicitly: `when
      // prompt(...) as Boolean` restates the position's implied type and must be silent. This is
      // the one place a wrong `Bool` spelling in `checkPromptAscription`'s `when` wiring would
      // turn every correctly-ascribed condition into an Error -- `Bool.kind` and the parser's
      // `Boolean` keyword both happening to read "Boolean" is an assumption worth a real test, not
      // just inspection.
      errorsFor(
        entityModel("""when prompt("is it valid") as Boolean then { do "yes" } end"""),
        "when-restate"
      ) mustBe empty
    }
  }

  "a contradicting ascription" should {
    "be an Error on a 'let' whose ascription names a different type" in { (td: TestData) =>
      val errs =
        errorsFor(entityModel("""let x: Score = prompt("d") as Real"""), "let-contradict")
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("contradicts")) mustBe true
      }
    }

    "be an Error on a 'constant' whose ascription names a different type" in { (td: TestData) =>
      val errs = errorsFor(
        constantModel("""constant G: Real = prompt("gravity") as Score"""),
        "constant-contradict"
      )
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("contradicts")) mustBe true
      }
    }

    "be an Error on a 'set' whose ascription names a different type" in { (td: TestData) =>
      val errs = errorsFor(
        setModel("""set field Acct.balance to prompt("balance") as Real"""),
        "set-contradict"
      )
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("contradicts")) mustBe true
      }
    }

    "be an Error on a 'when' condition ascribed to a non-Boolean type" in { (td: TestData) =>
      val errs = errorsFor(
        entityModel("""when prompt("is it valid") as Score then { do "yes" } end"""),
        "when-contradict"
      )
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("contradicts")) mustBe true
      }
    }
  }

  "the seam warning" should {
    "fire on the ONE case the ruling names: an unascribed 'let' with no declared type" in {
      (td: TestData) =>
        val warns = completenessFor(entityModel("""let x = prompt("d")"""), "let-untyped")
        withClue(warns.map(_.message).mkString("\n")) {
          warns.exists(_.message.contains("untyped")) mustBe true
        }
    }

    // NEGATIVE CONTROLS -- these are the false positives the ruling exists to prevent. Without
    // these, a future "fix" that widens the warning to every unwired position has nothing to break.

    "stay silent on a constructor argument -- deliberately unwired" in { (td: TestData) =>
      // `record Estimate(...)` -- the keyword-led Constructor form, matching the working shape in
      // TypedHoleTest's `record Line(sku = prompt(...))`. Other, UNRELATED completeness warnings
      // are expected here (no repository, no Id type, …) -- the assertion is narrowly that NONE of
      // them is the typed-hole seam warning this task adds.
      val src =
        s"""domain D is {
           |  context C is {
           |    type Score is Real
           |    record Estimate is { amount: Score }
           |    command Go is { why: String }
           |    entity E is {
           |      handler H is {
           |        on command Go is { let r = record Estimate(amount = prompt("estimate")) }
           |      }
           |    }
           |  }
           |}
           |""".stripMargin
      completenessFor(src, "ctor-arg-untyped").exists(_.message.contains("untyped")) mustBe false
    }

    "stay silent on a 'when' condition with no ascription -- must be wired to Boolean" in {
      (td: TestData) =>
        // Same narrowing as above: `entityModel` produces other unrelated completeness noise (no
        // repository, no outlet, …); only the typed-hole seam warning is under test here.
        val warns = completenessFor(
          entityModel("""when prompt("is it valid") then { do "yes" } end"""),
          "when-untyped"
        )
        warns.exists(_.message.contains("untyped")) mustBe false
    }

    "stay silent on a 'set' with no ascription -- deliberately unwired" in { (td: TestData) =>
      completenessFor(
        setModel("""set field Acct.balance to prompt("balance")"""),
        "set-untyped"
      ).exists(_.message.contains("untyped")) mustBe false
    }
  }
}
