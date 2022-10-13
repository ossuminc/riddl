/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.translator.git
import com.reactific.riddl.testkit.RunCommandOnExamplesTest
import com.reactific.riddl.translator.hugo_git_check.GitCheckCommand

import java.nio.file.Path

class GitCheckTranslatorTest
  extends RunCommandOnExamplesTest[
  GitCheckCommand.Options, GitCheckCommand]("git-check") {

  val output: String = "hugo-git-check/target/test"

  def makeTranslatorOptions(fileName: String): GitCheckCommand.Options = {
    val gitCloneDir = Path.of(".").toAbsolutePath.getParent
    val relativeDir = Path.of(".").resolve(fileName).getParent
    GitCheckCommand.Options(
      Some(gitCloneDir), Some(relativeDir)
    )
  }

  "HugoGitCheck" should {
    "run stuff when git changes" in {
      runTests()
    }
  }
}
