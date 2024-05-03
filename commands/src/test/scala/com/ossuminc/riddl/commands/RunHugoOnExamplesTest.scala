/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{CommandOptions, CommandPlugin}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.testkit.RunCommandOnExamplesTest
import com.ossuminc.riddl.hugo.HugoPass
import org.scalatest.Assertion

import java.nio.file.{Files, Path}
import scala.annotation.unused

/** Unit Tests To Run Riddlc On Examples */
class RunHugoOnExamplesTest
    extends RunCommandOnExamplesTest[HugoPass.Options, HugoCommand]("hugo", shouldDelete = false) {

  val validTestNames = Seq("ReactiveBBQ", "dokn", "ReactiveSummit")

  override def validateTestName(name: String): Boolean = validTestNames.exists(name.endsWith)

  "Run Hugo On Examples" should {
    "correctly process ReactiveBBQ" in {
      runTest("ReactiveBBQ")

    }
    "correctly process dokn" in {
      runTest("dokn")
    }
  }

  override def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused passesResult: PassesResult,
    @unused tempDir: Path
  ): Assertion = {
    info(s"Hugo output in ${tempDir.toString}")
    val themes = tempDir.resolve("themes").resolve("hugo-geekdoc")
    Files.isDirectory(themes) mustBe true
    val toml = tempDir.resolve("config.toml")
    Files.isReadable(toml) mustBe true
    Files.isRegularFile(toml) mustBe true
    passesResult.commonOptions mustBe commonOptions
    succeed
  }
}
