/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{StringBuildingPrintStream, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A command's PRODUCT goes to stdout; diagnostics go to stderr.
  *
  * This is a regression gate, and it exists because the boundary broke silently. 2.0.0-rc.24 moved
  * the logger to stderr -- correct for `validate`'s diagnostics -- and thereby emptied the stdout of
  * `version`, `info`, `about` and `help`, which emit through that same logger. sbt-riddl reads
  * `riddlc version` from stdout, so it got an empty string and every build gate pinning rc.24 died
  * with `riddlc version () has insufficient semantic versioning parts`.
  *
  * **Nothing failed at the riddl end.** Every suite was green: no test asserted which STREAM these
  * commands use, so the contract existed only in a consumer's expectations. That is what this file
  * fixes. `version` is the load-bearing one -- a build plugin parses it -- so it is asserted to be
  * exactly the bare version with no `[info]` prefix and no decoration.
  */
class ProductGoesToStdoutTest extends AnyWordSpec with Matchers {

  /** Runs `f` with both standard streams captured. */
  private def capture[A](f: () => A): (String, String) = {
    val (oldOut, oldErr) = (System.out, System.err)
    val outStream = StringBuildingPrintStream()
    val errStream = StringBuildingPrintStream()
    synchronized {
      try
        System.setOut(outStream)
        System.setErr(errStream)
        f()
        outStream.flush(); errStream.flush()
        (outStream.mkString(), errStream.mkString())
      finally
        System.setOut(oldOut)
        System.setErr(oldErr)
    }
  }

  "riddlc version" should {
    "print the bare version to STDOUT, with no prefix" in {
      val (out, _) = capture(() => new VersionCommand().run(VersionCommand.Options("version"), None))
      val text = out.trim
      withClue(s"stdout was '$text': ") {
        text must not be empty
        // What sbt-riddl's `versionTriple` needs: something with three dot-separated parts before
        // any `-rc.N` suffix. An `[info] ` prefix would be stripped by the plugin today, but the
        // plugin should not have to -- this is data.
        text must not include "[info]"
        text.takeWhile(_ != '-').split('.').length mustBe 3
      }
    }
  }

  "riddlc info" should {
    "print the build information to STDOUT" in {
      val (out, _) = capture(() => new InfoCommand().run(InfoCommand.Options(), None))
      withClue(s"stdout was '${out.take(200)}': ") { out.trim must not be empty }
    }
  }

  "riddlc about" should {
    "print its blurb to STDOUT" in {
      val (out, _) = capture(() => new AboutCommand().run(AboutCommand.Options(), None))
      withClue(s"stdout was '${out.take(200)}': ") { out.trim must not be empty }
    }
  }

  "riddlc help" should {
    "print the usage to STDOUT" in {
      val (out, _) = capture(() => new HelpCommand().run(HelpCommand.Options(), None))
      withClue(s"stdout was '${out.take(200)}': ") { out must include("Usage") }
    }
  }
}
