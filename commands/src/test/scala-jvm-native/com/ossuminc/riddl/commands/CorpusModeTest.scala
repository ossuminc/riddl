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

/** `riddlc <command> --corpus <dir>` — one process over many models (riddl-models, 2026-08-25).
  *
  * Every corpus-wide operation there is a shell loop starting a process per model — 188 for a
  * warning sweep, 190 to regenerate `.bast`, 188x3 for the round-trip check, run a dozen times in a
  * session. Measured on the native binary: **154s as a shell loop versus 8s in one process** over
  * the same 190 models, so process start genuinely dominates.
  *
  * Two properties matter more than the speed:
  *
  *   - **A failing model must not abort the rest.** A sweep exists to find every problem; stopping
  *     at the first makes it report a prefix of the truth.
  *   - **The total is riddlc's, not the caller's.** riddl-models' scripts each accumulate their own
  *     denominator, and getting a denominator right is exactly where they keep going wrong.
  */
class CorpusModeTest extends AnyWordSpec with Matchers {

  private def model(name: String, body: String, dir: Path): Unit = {
    val d = dir.resolve(name)
    Files.createDirectories(d)
    Files.writeString(d.resolve(s"$name.riddl"), body)
    Files.writeString(d.resolve(s"$name.conf"), s"""validate { input-file = "$name.riddl" }\n""")
  }

  private val good =
    """domain D is {
      |  context C is {
      |    command Go is { who: Id(C.E)  what: String(1,9) }
      |    record R is { a: String(1,9) }
      |    entity E is {
      |      state S of record C.R is { ??? }
      |      handler H is { on command C.Go is { do "handle" } }
      |    }
      |  }
      |}
      |""".stripMargin

  /** Names a command that does not exist, so resolution fails. */
  private val bad =
    """domain D is {
      |  context C is {
      |    record R is { a: String(1,9) }
      |    entity E is {
      |      state S of record C.R is { ??? }
      |      handler H is { on command C.Nope is { do "handle" } }
      |    }
      |  }
      |}
      |""".stripMargin

  private def withCorpus(models: Seq[(String, String)])(check: (Int, String) => Unit): Unit = {
    val dir = Files.createTempDirectory("riddl-corpus")
    try
      models.foreach { case (n, b) => model(n, b, dir) }
      val (rc, out) = pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
        StdStreamCapture.capturingStdOut { () =>
          Commands.runMain(Array("validate", "--corpus", dir.toString))
        }
      }
      check(rc, out)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  "corpus mode" should {

    "run every model found beneath the root and report a total" in {
      withCorpus(Seq("alpha" -> good, "beta" -> good, "gamma" -> good)) { (rc, out) =>
        withClue(s"rc=$rc out='$out'") {
          out must include("3 models")
          out must include("0 failed")
          rc mustBe 0
        }
      }
    }

    "NOT abort when one model fails — the rest still run" in {
      // The acceptance criterion that matters. `bad` sits between two good models, so a run that
      // stopped at the first failure would report fewer than 3.
      withCorpus(Seq("alpha" -> good, "beta" -> bad, "gamma" -> good)) { (rc, out) =>
        withClue(s"rc=$rc out='$out'") {
          out must include("3 models")
          out must include("1 failed")
          out must include("2 ok")
          rc must not be 0
        }
      }
    }

    "report a non-zero exit when any model fails" in {
      withCorpus(Seq("only" -> bad)) { (rc, out) =>
        withClue(s"rc=$rc out='$out'") {
          rc must not be 0
          out must include("1 failed")
        }
      }
    }

    "say so when the root holds no entry points" in {
      // Silence here would be indistinguishable from a clean corpus — the defect this whole batch
      // of requests keeps circling.
      val dir = Files.createTempDirectory("riddl-corpus-empty")
      try
        val rc = pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
          Commands.runMain(Array("validate", "--corpus", dir.toString))
        }
        rc must not be 0
      finally Files.delete(dir)
    }
  }
}
