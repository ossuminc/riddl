package com.reactific.riddl.translator.git

import com.reactific.riddl.language.testkit.RunCommandOnExamplesTest
import com.reactific.riddl.translator.hugo_git_check.GitCheckCommand
import org.scalatest.Assertion

import java.nio.file.Path

class GitCheckTranslatorTest
  extends RunCommandOnExamplesTest[
    GitCheckCommand.Options, GitCheckCommand
  ]("git-check", Path.of(".")) {

  val output: String = "hugo-git-check/target/test"

  def makeTranslatorOptions(fileName: String): GitCheckCommand.Options = {
    val gitCloneDir = Path.of(".").toAbsolutePath.getParent
    val relativeDir = Path.of(".").resolve(fileName).getParent
    GitCheckCommand.Options(
      Some(gitCloneDir), Some(relativeDir)
    )
  }

  override def onSuccess(
    commandName: String,
    caseName: String,
    configFile: Path,
    outDir: Path
  ): Assertion = {
    succeed
  }


  "HugoGitCheck" should {
    "run stuff when git changes" in {
      runTests()
    }
  }
}
