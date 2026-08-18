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
  * it fires ONLY on an unascribed `let x = prompt(…)` with no declared type -- the ONE position the
  * riddl-models corpus measurement showed was actually unascribed (0 of 288 uses). Every other
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

  private def usageWarningsFor(src: String, origin: String): Messages =
    diagnostics(src, origin).filter(_.kind == Messages.UsageWarning)

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

  /** Carries everything the four argument-bearing positions need in one model: a record to
    * construct, a function to call, an entity with `on init`/`on term` parameters to initiate and
    * terminate, and an invariant with a `requires` type. `Score` and `Other` are two DECLARED types
    * with the same underlying `Real`, which is the point -- the comparison is syntactic, so two
    * names that resolve alike must still contradict.
    */
  private def argModel(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type Score is Real
       |    type Other is Real
       |    record Params is { amount: Score } with { briefly "params" }
       |    record Sum is { s: Score } with { briefly "sum" }
       |    record Estimate is { amount: Score } with { briefly "rec" }
       |    command Go is { why: String } with { briefly "cmd" }
       |    function Rate is {
       |      requires record Params
       |      returns record Sum
       |      return record Sum(s = prompt("s") as Score)
       |    } with { briefly "fn" }
       |    invariant Positive requires record Params is "amount is positive" with {
       |      briefly "inv"
       |    }
       |    entity Target is {
       |      handler TH is {
       |        on init(seed: Score) { do "start" }
       |        on term(why: Score) { do "end" }
       |      } with { briefly "th" }
       |    } with { briefly "t" }
       |    entity E is {
       |      handler H is { on command Go is { $stmt } }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def functionModel(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type Score is Real
       |    record Sum is { s: Score } with { briefly "sum" }
       |    record Other is { o: Score } with { briefly "other" }
       |    function Rate is {
       |      returns record Sum
       |      $stmt
       |    } with { briefly "fn" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def appModel(stmt: String): String =
    s"""domain D is {
       |  application context App is {
       |    type Greeting is String
       |    type Other is String
       |    command Refresh is { why: String } with { briefly "cmd" }
       |    group Main is {
       |      output Panel presents type Greeting
       |    } with { briefly "g" }
       |    handler Screen is {
       |      on command Refresh { $stmt }
       |    } with { briefly "h" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
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

  // Cross-context qualified paths, for the two "qualified restatement" cases below. `D.Common.X`
  // mirrors the fully-domain-qualified spelling `AskTest`'s cross-context fixtures already use.
  private def qualifiedModel(stmt: String): String =
    s"""domain D is {
       |  context Common is {
       |    type OrderId is String
       |    type ShippingId is String
       |  }
       |  context C is {
       |    command Go is { why: String }
       |    entity E is {
       |      handler H is { on command Go is { $stmt } }
       |    }
       |  }
       |}
       |""".stripMargin

  "the ascription's type reference (A20 review finding 3)" should {
    "be an Error when the ascription names a type that does not exist" in { (td: TestData) =>
      // Before the 2026-08-15 fix, `ResolutionPass.resolveValue`'s `PromptValue` arm resolved
      // nothing -- an ascription naming a nonexistent type validated clean.
      val errs =
        errorsFor(entityModel("""let x = prompt("d") as Nonexistent"""), "ascription-unresolved")
      withClue(errs.map(_.message).mkString("\n")) {
        errs.exists(_.message.contains("was not resolved")) mustBe true
      }
    }

    "stay silent (no unresolved-path error) when the ascription names a real type" in {
      (td: TestData) =>
        val errs = errorsFor(entityModel("""let x = prompt("d") as Score"""), "ascription-resolved")
        withClue(errs.map(_.message).mkString("\n")) {
          errs.exists(_.message.contains("was not resolved")) mustBe false
        }
    }

    "NOT flag a type as unused when its only reference is an ascription" in { (td: TestData) =>
      // `entityModel`'s `type Score is Real` is otherwise unused by this fixture -- no field, no
      // state, nothing but the ascription below names it. `resolveTypeExpression`'s
      // AliasedTypeExpression arm calls `associateUsage` internally, so calling it from the new
      // PromptValue arm is enough to populate `usedBy` -- this pins that it actually does, not
      // just that resolution itself succeeds (the previous case already covers that).
      val warns =
        usageWarningsFor(entityModel("""let x = prompt("d") as Score"""), "ascription-usedby")
      withClue(warns.map(_.message).mkString("\n")) {
        warns.exists(w => w.message.contains("Score") && w.message.contains("unused")) mustBe false
      }
    }
  }

  "a restated ascription" should {
    "be silent on a 'let' whose ascription matches its declared type" in { (td: TestData) =>
      errorsFor(
        entityModel("""let x: Score = prompt("d") as Score"""),
        "let-restate"
      ) mustBe empty
    }

    "be silent on a 'let' whose declared type matches a Cardinality-wrapped ascription (review" +
      " finding 1)" in { (td: TestData) =>
        // Before the fix, `typeAscriptionName` named `Optional(AliasedTypeExpression(Score))` by
        // its Scala class ("Optional") rather than recursing to "Score", so this compared
        // "Optional" against "Score" and reported a false contradiction on legal code.
        errorsFor(
          entityModel("""let x: Score = prompt("d") as Score?"""),
          "let-restate-optional"
        ) mustBe empty
      }

    "be silent on a 'let' whose qualified ascription restates its qualified declared type" +
      " (review finding 4)" in { (td: TestData) =>
        // Before the fix, the expected side was rebuilt as the bare simple name ("OrderId") while
        // the ascribed side kept the full written path ("D.Common.OrderId"), so this compared
        // unequal and reported a false contradiction against itself.
        errorsFor(
          qualifiedModel("""let x: D.Common.OrderId = prompt("d") as D.Common.OrderId"""),
          "let-restate-qualified"
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

    "be an Error on a 'let' whose qualified ascription names a genuinely different qualified" +
      " type (review finding 4, the other direction)" in { (td: TestData) =>
        // The last-path-segment comparison that fixes the qualified-restatement false positive
        // above must not become vacuous: two DIFFERENTLY-named qualified types must still
        // contradict.
        val errs = errorsFor(
          qualifiedModel(
            """let x: D.Common.OrderId = prompt("d") as D.Common.ShippingId"""
          ),
          "let-contradict-qualified"
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

    // ------------------------------------------------------------------------------------------
    // The seven positions `checkPromptAscription` reaches. Four of them (constructor, call,
    // initiate, terminate arguments) share ONE wiring point, `checkArgumentTypes` -- that helper's
    // own scaladoc records that two hand-written copies were free to drift, and these four would
    // have been four more copies. Each Error case is paired with an agreement case, because a
    // check wired to reject everything would satisfy the Error half alone.
    // ------------------------------------------------------------------------------------------

    "be an Error on a CONSTRUCTOR argument whose ascription contradicts the field" in {
      (td: TestData) =>
        val src = argModel("""let r = record Estimate(amount = prompt("e") as Other)""")
        errorsFor(src, td.name).map(_.message).mkString("\n") must include("contradicts")
    }

    "be silent on a CONSTRUCTOR argument whose ascription agrees with the field" in {
      (td: TestData) =>
        val src = argModel("""let r = record Estimate(amount = prompt("e") as Score)""")
        errorsFor(src, td.name).map(_.message).mkString("\n") must not include "contradicts"
    }

    "be an Error on a CALL argument whose ascription contradicts the parameter" in {
      (td: TestData) =>
        val src = argModel("""let q = call function Rate(amount = prompt("r") as Other)""")
        errorsFor(src, td.name).map(_.message).mkString("\n") must include("contradicts")
    }

    "be an Error on an INITIATE argument whose ascription contradicts the parameter" in {
      (td: TestData) =>
        val src = argModel("""let i = initiate entity Target(seed = prompt("s") as Other)""")
        errorsFor(src, td.name).map(_.message).mkString("\n") must include("contradicts")
    }

    "be an Error on a TERMINATE argument whose ascription contradicts the parameter" in {
      (td: TestData) =>
        val src = argModel(
          """let i = initiate entity Target(seed = prompt("s") as Score)
            |            terminate i with (why = prompt("w") as Other)""".stripMargin
        )
        errorsFor(src, td.name).map(_.message).mkString("\n") must include("contradicts")
    }

    "be an Error on a RETURN value whose ascription contradicts the function's returns" in {
      (td: TestData) =>
        errorsFor(functionModel("""return prompt("r") as Other"""), td.name)
          .map(_.message)
          .mkString("\n") must include("contradicts")
    }

    "be silent on a RETURN value whose ascription agrees with the function's returns" in {
      (td: TestData) =>
        errorsFor(functionModel("""return prompt("r") as Sum"""), td.name)
          .map(_.message)
          .mkString("\n") must not include "contradicts"
    }

    "be an Error on a REQUIRE argument whose ascription contradicts the invariant" in {
      (td: TestData) =>
        val src = argModel("""require invariant Positive with prompt("p") as Other""")
        errorsFor(src, td.name).map(_.message).mkString("\n") must include("contradicts")
    }

    "be an Error on a PUT value whose ascription contradicts the output" in { (td: TestData) =>
      errorsFor(appModel("""put prompt("g") as Other to output Panel"""), td.name)
        .map(_.message)
        .mkString("\n") must include("contradicts")
    }

    "be silent on a PUT value whose ascription agrees with the output" in { (td: TestData) =>
      errorsFor(appModel("""put prompt("g") as Greeting to output Panel"""), td.name)
        .map(_.message)
        .mkString("\n") must not include "contradicts"
    }

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
