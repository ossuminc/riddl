/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.translator.onchange

import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.commands.InputFileCommandPlugin
import com.reactific.riddl.commands.ParseCommand
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.Path
import scala.annotation.unused

class OnChangeTranslatorTest
    extends RunCommandOnExamplesTest[
      InputFileCommandPlugin.Options,
      ParseCommand
    ](ParseCommand.cmdName) {

  val output: String = s"${OnChangeCommand.cmdName}/target/test"

  def makeTranslatorOptions(fileName: String): OnChangeCommand.Options = {
    val gitCloneDir = Path.of(".").toAbsolutePath.getParent
    val relativeDir = Path.of(".").resolve(fileName).getParent
    OnChangeCommand.Options(Some(gitCloneDir), Some(relativeDir))
  }

  val root = "onchange/src/test/input/"

  "OnChangeCommand" should {
    "handle simple case" in {
      runTest(root + "simple")
    }
    "handle harder case" in {
      runTest(root + "harder")
    }
  }

  override def onSuccess(
    commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused command: CommandPlugin[CommandOptions],
    @unused tempDir: Path
  ): Assertion = {
    info(s"Case $caseName at $configFile succeeded")
    succeed
  }

  override def onFailure(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused messages: Messages,
    @unused tempDir: Path
  ): Assertion = { fail(messages.format) }
}
