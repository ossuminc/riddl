package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.hugo.HugoCommand
import com.reactific.riddl.language.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.Path
import scala.annotation.unused

/** Unit Tests To Run Riddlc On Examples */

// import java.nio.file.{Files, Path}

class RunHugoOnExamplesTest extends
  RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand](
    "hugo") {

  val output: String = "target/test/hugo-examples"

  def makeTranslatorOptions (fileName: String): HugoCommand.Options = {
    val gitCloneDir = Path.of(".").toAbsolutePath.getParent
    val relativeDir = Path.of(".").resolve(fileName).getParent
    HugoCommand.Options(
      Some(gitCloneDir), Some(relativeDir)
    )
  }

  "Run Hugo On Examples" should {
    "work " in {
      runTests()
    }
  }

  override def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused command: CommandPlugin[CommandOptions]
  ): Assertion = {
    // TODO: check themes dir
    // TODO: check config.toml setting values
    // TODO: check options
    succeed
  }
}

