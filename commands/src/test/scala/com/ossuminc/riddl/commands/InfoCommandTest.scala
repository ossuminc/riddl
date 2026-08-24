/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{StringBuildingPrintStream, ThirdPartyNotices}

/** Unit Tests For InfoCommand.
  *
  * The attribution block must be the LAST thing `riddlc info` prints. That ordering is easy to
  * break without noticing: `InfoFormatter.formatInfo` already appends the notices, so calling it
  * here instead of `formatBuildInfo` puts them ABOVE the JVM/OS lines this command adds — which is
  * exactly what happened when the block was first added. A test on InfoFormatter alone cannot catch
  * it, because InfoFormatter is not where the ordering is decided.
  */
class InfoCommandTest extends CommandTestBase("commands/input") {

  /** Captures STDOUT, which is where `info` writes as of 2026-08-24.
    *
    * It read the LOGGER until then. `info`'s output is the command's PRODUCT, not a diagnostic, so
    * it moved to stdout when rc.24's logger-to-stderr change emptied its stdout entirely and broke
    * sbt-riddl's version check. The assertions below are unchanged — only the stream they watch has
    * moved, which is the point.
    */
  private def infoOutput: Seq[String] = {
    val old = System.out
    val captured = StringBuildingPrintStream()
    synchronized {
      try
        System.setOut(captured)
        runCommand(Seq("info"))
        captured.flush()
        captured.mkString().split("\n").toSeq
      finally System.setOut(old)
    }
  }

  "InfoCommand" should {

    "run correctly" in {
      runCommand(Seq("info"))
    }

    "print the third-party attribution LAST, below the JVM details" in {
      val lines = infoOutput
      val lastMeaningful = lines.filter(_.trim.nonEmpty).last
      lastMeaningful must include("riddl itself is")

      val jvmLine = lines.indexWhere(_.contains("jvm version"))
      val noticesLine = lines.indexWhere(_.contains("Third-Party Software"))
      jvmLine must be >= 0
      noticesLine must be >= 0
      // The whole point: attribution comes after everything else.
      noticesLine must be > jvmLine
    }

    "name both places a user can read the full license texts" in {
      val text = infoOutput.mkString("\n")
      text must include(ThirdPartyNotices.noticesFile)
      text must include(ThirdPartyNotices.noticesUrl)
    }

    "still report the build information it always did" in {
      val text = infoOutput.mkString("\n")
      text must include("version:")
      text must include("scala version:")
    }
  }
}
