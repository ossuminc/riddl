package com.reactific.riddl.commands
import org.scalatest.Assertion
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

/** Unit Tests For ReadOptionsTest */
class ReadOptionsTest extends AnyWordSpec with Matchers {

  val inputDir = "commands/src/test/input/"
  val confFile = s"$inputDir/cmdoptions.conf"

  def check[OPTS <: CommandOptions](
    cmd: CommandPlugin[?],
    expected: OPTS,
    file: Path = Path.of(confFile)
  ): Assertion = {
    cmd.loadOptionsFrom(file) match {
      case Left(errors)   => fail(errors.format)
      case Right(options) => options must be(expected)
    }
  }

  "Options" should {
    "read for dump" in {
      val expected = InputFileCommandPlugin
        .Options(Some(Path.of(s"$inputDir/dump.riddl")), "dump")
      check(new DumpCommand, expected)
    }
    "read for from" in {
      val expected = FromCommand
        .Options(Some(Path.of(s"$inputDir/file.conf")), "dump")
      check(new FromCommand, expected)
    }

    "read for onchange" in {
      val expected = OnChangeCommand.Options(
        configFile = Path.of(s"$inputDir/onchange.riddl"),
        watchDirectory = Path.of(s"$inputDir"),
        targetCommand = "parse",
        interactive = true
      )
      expected.command must be(OnChangeCommand.cmdName)
      check(new OnChangeCommand, expected)
    }

    "make sure onchange doesn't accept empty strings" in {
      val expected = OnChangeCommand.Options()
      intercept[TestFailedException](check(
        new OnChangeCommand,
        expected,
        Path.of(s"$inputDir/onchangevalidation.conf")
      ))

    }

    "read for parse" in {
      val expected = InputFileCommandPlugin
        .Options(Some(Path.of(s"$inputDir/parse.riddl")), "parse")
      check(new ParseCommand, expected)
    }
    "read for stats" in {
      val expected = InputFileCommandPlugin
        .Options(Some(Path.of(s"$inputDir/stats.riddl")), "stats")
      check(new StatsCommand, expected)
    }
    "read for validate" in {
      val expected = InputFileCommandPlugin
        .Options(Some(Path.of(s"$inputDir/validate.riddl")), "validate")
      check(new ValidateCommand, expected)
    }
    "read common options" in {
      CommandOptions.loadCommonOptions(Path.of(confFile)) match {
        case Left(errors) => fail(errors.format)
        case Right(options) =>
          options.debug must be(true)
          options.showTimes must be(true)
          options.verbose must be(false)
          options.dryRun must be(false)
          options.showWarnings must be(true)
          options.showMissingWarnings must be(false)
          options.showStyleWarnings must be(false)
          options.showUnusedWarnings must be(true)
          options.pluginsDir must be(None)
          options.sortMessagesByLocation must be(false)
      }
    }
  }
}
