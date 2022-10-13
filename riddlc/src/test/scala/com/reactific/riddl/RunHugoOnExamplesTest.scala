/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl

import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.hugo.HugoCommand
import com.reactific.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.Path
import scala.annotation.unused

/** Unit Tests To Run Riddlc On Examples */

// import java.nio.file.{Files, Path}

class RunHugoOnExamplesTest
    extends RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand]("hugo") {

  override val outDir: Path = Path.of("riddlc/target/test/hugo-examples")

  "Run Hugo On Examples" should { "work " in { runTests() } }

  override def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused command: CommandPlugin[CommandOptions],
    @unused tempDir: Path
  ): Assertion = {
    // TODO: check themes dir
    // TODO: check config.toml setting values
    // TODO: check options
    succeed
  }
}
