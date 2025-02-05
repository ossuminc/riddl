/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommonOptionsHelper
import com.ossuminc.riddl.commands.{CommandTestBase, InputFileCommand}
import com.ossuminc.riddl.language.Messages.Messages
import org.scalatest.exceptions.TestFailedException

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Unit Tests For ReadRiddlOptionsTest */
class ReadRiddlOptionsTest extends CommandTestBase("commands/input/") {

  "RiddlOptions" should {
    "read for dump" in {
      val expected = InputFileCommand
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
      intercept[TestFailedException] {
        check[OnChangeCommand.Options](new OnChangeCommand, expected, Path.of(s"$inputDir/onchangevalidation.conf")) {
          (opts: OnChangeCommand.Options) =>
            opts.check must be(empty)
            opts.refreshRate must be(10.seconds)
            opts.maxCycles must be(OnChangeCommand.defaultMaxLoops)
            opts.configFile must not(be(Path.of("")))
            opts.watchDirectory must not(be(Path.of("")))
            opts.targetCommand must not(be(empty))
            opts.interactive must be(true)
        }
      }
    }

    "read for parse" in {
      val expected = InputFileCommand
        .Options(Some(Path.of(s"$inputDir/parse.riddl")), "parse")
      check(new ParseCommand, expected)
    }
    "read for validate" in {
      val expected = InputFileCommand
        .Options(Some(Path.of(s"$inputDir/validate.riddl")), "validate")
      check(new ValidateCommand, expected)
    }
    "read common options" in {
      CommonOptionsHelper.loadCommonOptions(Path.of(confFile)) match {
        case Left(errors) => fail(errors.format)
        case Right(options) =>
          options.debug must be(true)
          options.showTimes must be(true)
          options.verbose must be(false)
          options.dryRun must be(false)
          options.showWarnings must be(true)
          options.showMissingWarnings must be(false)
          options.showStyleWarnings must be(false)
          options.showUsageWarnings must be(true)
          options.sortMessagesByLocation must be(false)
      }
    }
  }
}
