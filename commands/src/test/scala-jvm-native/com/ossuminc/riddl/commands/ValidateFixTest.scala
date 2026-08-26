/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** `validate --fix`: each rule ships its own codemod.
  *
  * The rule carries the fix, so a rule and its remedy cannot drift apart. These cases assert the
  * FILE ON DISK, not the reported count -- a fixer that says it fixed something and wrote nothing
  * is the confident-wrong-answer shape this repo keeps recording.
  */
class ValidateFixTest extends AnyWordSpec with Matchers {

  private val deprecated =
    """domain Foo is {
      |  context Bar is {
      |    command Cmd is { ??? }
      |    entity Baz is {
      |      handler H is {
      |        on command Cmd is { prompt "figure it out" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def withModel(body: String)(
    check: (ValidateCommand.Options => Unit, () => String) => Unit
  ): Unit = {
    val dir = Files.createTempDirectory("riddl-validate-fix")
    try
      val f = dir.resolve("m.riddl")
      Files.writeString(f, body)
      def run(opts: ValidateCommand.Options): Unit =
        pc.withOptions(CommonOptions()) { _ =>
          StdStreamCapture.capturingStdOut { () =>
            new ValidateCommand().run(opts.copy(inputFile = Some(f)), None)
          }
        }
        ()
      check(run, () => Files.readString(f))
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  "validate --fix" should {

    "rewrite the source on disk, not merely report" in {
      withModel(deprecated) { (run, read) =>
        read() must include("prompt \"figure it out\"")
        run(ValidateCommand.Options(fix = true))
        val after = read()
        after must include("do \"figure it out\"")
        after mustNot include("prompt \"figure it out\"")
      }
    }

    "leave a model with nothing to fix byte-identical" in {
      // The no-op path writes nothing at all. Asserting byte equality catches a fixer that
      // "successfully" rewrites a file to itself with, say, different line endings.
      val clean = "domain Foo is { ??? }\n"
      withModel(clean) { (run, read) =>
        run(ValidateCommand.Options(fix = true))
        read() mustBe clean
      }
    }

    "apply only the named rule under --fix-rule" in {
      withModel(deprecated) { (run, read) =>
        run(ValidateCommand.Options(fix = true, fixRule = Some(RuleId.DoStatement.code)))
        read() must include("do \"figure it out\"")
      }
    }

    "report what it did NOT fix, and why" in {
      // Their design note: "a codemod that silently leaves 40 sites is worse than one that fixes
      // none". The model below trips rules that carry no mechanical fix, so the run must SAY so.
      val dir = Files.createTempDirectory("riddl-fix-skips")
      try
        val f = dir.resolve("m.riddl")
        Files.writeString(f, deprecated)
        val (_, out) = pc.withOptions(CommonOptions()) { _ =>
          StdStreamCapture.capturingStdOut { () =>
            new ValidateCommand().run(ValidateCommand.Options(Some(f), fix = true), None)
          }
        }
        out must include("not fixed")
        out must include("no mechanical fix")
      finally
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
    }

    "do nothing when --fix-rule names a rule the model does not trip" in {
      withModel(deprecated) { (run, read) =>
        run(ValidateCommand.Options(fix = true, fixRule = Some(RuleId.AbstractType.code)))
        // The deprecation this model DOES have must survive, or the filter is not filtering.
        read() must include("prompt \"figure it out\"")
      }
    }
  }

  "validate --fix-dry-run" should {

    "show the diff and write NOTHING" in {
      withModel(deprecated) { (run, read) =>
        val before = read()
        run(ValidateCommand.Options(fixDryRun = true))
        // Byte-identical: a dry run that "helpfully" normalised whitespace would be a write.
        read() mustBe before
      }
    }

    "not be spelled --dry-run, because the global flag short-circuits the command" in {
      // Commands.handleCommandRun returns before invoking anything when the GLOBAL dryRun is set,
      // logging "Would have executed...". So a `validate --dry-run` could never reach the fixer at
      // all -- `find` needed its own `-dry-run` for the same reason. This pins the reason, not the
      // spelling: if someone "tidies" the name to --dry-run, the fixer silently stops running.
      ValidateCommand.Options(fixDryRun = true).fixDryRun mustBe true
    }
  }

  "a COMPUTED fix" should {

    "rewrite the span using the text it matched" in {
      // [1.16]: `mechanicalFix` was `Option[String]` -- a CONSTANT -- so a fix whose replacement
      // depends on what it matched could not be expressed at all. `quoted-constant-literal` is a
      // pure span replacement (`"5"` -> `5`) and was excluded purely for want of a way to say so.
      val model =
        """domain D is {
          |  context C is {
          |    constant Threshold: Integer = "5"
          |    constant Ratio: Real = "1.50"
          |  } with { briefly "c" described as "c" }
          |} with { briefly "d" described as "d" }
          |""".stripMargin
      withModel(model) { (run, read) =>
        run(ValidateCommand.Options(fix = true))
        val after = read()
        after must include("constant Threshold: Integer = 5")
        // `1.50`, not `1.5`: the replacement is the matched text minus its quotes, so precision
        // written by the author survives. A parsed-and-reprinted number would not.
        after must include("constant Ratio: Real = 1.50")
        after mustNot include("\"5\"")
      }
    }

    "not appear in the constant-replacement map, which cannot carry it" in {
      // The published Map[String, String] is handed to consumers that apply replacements blindly.
      // A computed fix has no constant text, so omitting it is the honest answer rather than
      // inventing one.
      RuleId.mechanicalReplacements.keySet mustNot contain(RuleId.QuotedConstantLiteral.code)
      RuleId.fixable.keySet must contain(RuleId.QuotedConstantLiteral.code)
    }
  }

  "the fixable set" should {
    "contain only rules whose fix is a pure span replacement" in {
      // shape-keyword rewrites `flow X is` to `processor X as flow is` -- an insertion elsewhere
      // than the reported span -- and state-is-record may need to INSERT a keyword. Applying either
      // as a span swap corrupts source, so their absence here is load-bearing, not an oversight.
      RuleId.mechanicalReplacements.keySet mustNot contain(RuleId.ShapeKeyword.code)
      RuleId.mechanicalReplacements.keySet mustNot contain(RuleId.StateIsRecord.code)
      RuleId.mechanicalReplacements(RuleId.DoStatement.code) mustBe "do"
    }
  }
}
