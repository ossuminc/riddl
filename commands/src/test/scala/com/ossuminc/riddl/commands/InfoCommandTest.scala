/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{StringLogger, ThirdPartyNotices}
import com.ossuminc.riddl.utils.pc

/** Unit Tests For InfoCommand.
  *
  * The attribution block must be the LAST thing `riddlc info` prints. That ordering is easy to
  * break without noticing: `InfoFormatter.formatInfo` already appends the notices, so calling it
  * here instead of `formatBuildInfo` puts them ABOVE the JVM/OS lines this command adds — which is
  * exactly what happened when the block was first added. A test on InfoFormatter alone cannot catch
  * it, because InfoFormatter is not where the ordering is decided.
  */
class InfoCommandTest extends CommandTestBase("commands/input") {

  private def infoOutput: Seq[String] =
    pc.withLogger(StringLogger()) { logger =>
      runCommand(Seq("info"))
      logger.toString.split("\n").toSeq
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
