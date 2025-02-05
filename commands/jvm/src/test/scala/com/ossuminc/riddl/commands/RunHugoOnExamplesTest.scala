/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.Assertion

import java.nio.file.{Files, Path}
import scala.annotation.unused

/** Unit Tests To Run Riddlc On Examples */
class RunHugoOnExamplesTest extends RunCommandOnExamplesTest {

  val validTestNames = Seq("ReactiveBBQ", "dokn", "ReactiveSummit")

  override def validateTestName(name: String): Boolean = validTestNames.exists(name.endsWith)

  "Run Hugo On Examples" should {
    "correctly process ReactiveBBQ" in {
      runTest("ReactiveBBQ", "hugo")

    }
    "correctly process dokn" in {
      runTest("dokn", "hugo")
    }
  }

  override def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused passesResult: PassesResult,
    tempDir: Path
  ): Assertion = {
    info(s"Hugo output in ${tempDir.toString}")
    val themes = tempDir.resolve("themes").resolve("hugo-geekdoc")
    Files.isDirectory(themes) mustBe true
    val toml = tempDir.resolve("config.toml")
    Files.isReadable(toml) mustBe true
    Files.isRegularFile(toml) mustBe true
  }
}
