/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.TopLevelParser
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.utils.{Await, Logger, PlatformContext, URL}
import com.ossuminc.riddl.command.{Command, CommandOptions}

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

object ParseCommand {
  val cmdName = "parse"
}

/** A Command for Parsing RIDDL input
  */
class ParseCommand(using io: PlatformContext) extends InputFileCommand(ParseCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
        TopLevelParser
          .parseInput(rpi)
          .map(_ => PassesResult())
          .map(_ => PassesResult())
      }
      Await.result(future, 10.seconds)
    }
  }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
