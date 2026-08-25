/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** `validate` always says what it checked (riddl-models, 2026-08-25).
  *
  * **A command that prints nothing cannot be told apart from one that never ran.** riddl-models
  * produced a confident wrong zero three times in one day that way: `timeout` does not exist on
  * macOS (exit 127, empty output, "0 findings"); an option value form was rejected and riddlc
  * terminated; and `2>/dev/null` hid diagnostics entirely because they go to stderr. In every case
  * the correct observation was "this command did not do what you think", and the observable was
  * identical to a perfect corpus.
  *
  * The summary goes to STDOUT because it is the command's product, per the rc.24-3 rule that put
  * diagnostics on stderr.
  */
class ValidateSummaryTest extends AnyWordSpec with Matchers {

  /** Every case PINS the options.
    *
    * `pc.options` is global mutable state and sbt runs suites in parallel, so a suite that sets
    * `quiet` or turns warnings off changes what this one observes. These three cases passed alone
    * and failed in the full run for exactly that reason — the same class of defect as the stdout
    * capture race, one level up: shared global state, not a shared stream.
    */
  private def withModel(body: String)(check: (String, Boolean) => Unit): Unit = {
    val dir = Files.createTempDirectory("riddl-validate-summary")
    try
      val f = dir.resolve("m.riddl")
      Files.writeString(f, body)
      val (result, out) = pc.withOptions(CommonOptions()) { _ =>
        StdStreamCapture.capturingStdOut { () =>
          new ValidateCommand().run(ValidateCommand.Options(Some(f), None), None)
        }
      }
      check(out.trim, result.isRight)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  private def failOn(body: String, level: String): Boolean = {
    val dir = Files.createTempDirectory("riddl-validate-failon")
    try
      val f = dir.resolve("m.riddl")
      Files.writeString(f, body)
      pc.withOptions(CommonOptions()) { _ =>
        StdStreamCapture
          .capturingStdOut(() =>
            new ValidateCommand().run(ValidateCommand.Options(Some(f), Some(level)), None)
          )
          ._1
          .isLeft
      }
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  private val warnOnly =
    """domain D is {
      |  context C is { entity E is { ??? } }
      |}
      |""".stripMargin

  private val hasError =
    """domain D is {
      |  context C is { entity E is { handler H is { on command C.Nope is { do "x" } } } }
      |}
      |""".stripMargin

  "validate" should {
    "print a summary even when nothing is wrong" in {
      withModel(warnOnly) { (out, _) =>
        withClue(s"stdout was '$out': ") {
          out must not be empty
          out must include("definitions checked")
          out must include regex "\\d+ errors?"
          out must include regex "\\d+ warnings?"
        }
      }
    }

    "name the warning classes that were ENABLED" in {
      // Not decoration: 187 of riddl-models' 188 .conf files switch style and usage OFF, so running
      // through a .conf is quietly the LENIENT gate while a direct `validate <file>` is the strict
      // one — the opposite of what anyone assumes, with nothing in the output hinting at it.
      withModel(warnOnly) { (out, _) =>
        withClue(out) {
          out must (include("style").and(include("usage")).and(include("missing")))
        }
      }
    }

    "say so when warnings are switched off" in {
      val dir = Files.createTempDirectory("riddl-validate-off")
      try
        val f = dir.resolve("m.riddl")
        Files.writeString(f, warnOnly)
        val (_, out) = pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
          StdStreamCapture.capturingStdOut { () =>
            new ValidateCommand().run(ValidateCommand.Options(Some(f), None), None)
          }
        }
        withClue(out) { out must include("warnings off") }
      finally
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
    }

    "still print a summary for a model that has ERRORS" in {
      // The failing path returns Left so the exit code is unchanged, but silence there would be the
      // worst case of all: the run that most needs explaining.
      withModel(hasError) { (out, ok) =>
        withClue(out) {
          out must include("definitions checked")
          ok mustBe false
        }
      }
    }
  }

  "--fail-on" should {
    "treat every warning CLASS as a warning" in {
      // `style`, `usage` and `missing` are classes, not levels: each answers `isWarning`. An earlier
      // ladder ranked them BELOW `warning`, so `--fail-on warning` passed on a model full of style
      // warnings while `--fail-on style` failed — the opposite of what both names promise.
      failOn(warnOnly, "warning") mustBe true
      failOn(warnOnly, "info") mustBe true
    }

    "not fail at error level when there are only warnings" in {
      failOn(warnOnly, "error") mustBe false
      failOn(warnOnly, "severe") mustBe false
    }

    "fail at warning AND error level when there are errors" in {
      failOn(hasError, "warning") mustBe true
      failOn(hasError, "error") mustBe true
    }

    "leave the default alone: warnings do not fail a run" in {
      withModel(warnOnly) { (_, ok) => ok mustBe true }
    }
  }
}
