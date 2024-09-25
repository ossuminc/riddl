/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.TopLevelParser
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.Logger

import java.nio.file.Path
import com.ossuminc.riddl.command.{Command, CommandOptions}

object ParseCommand {
  val cmdName = "parse"
}

/** A Command for Parsing RIDDL input
  */
class ParseCommand extends InputFileCommand(ParseCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
                    options: Options,
                    commonOptions: CommonOptions,
                    log: Logger,
                    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      val rpi = RiddlParserInput.fromPath(inputFile)
      TopLevelParser.parseInput(rpi, commonOptions)
        .map(_ => PassesResult()).map(_ => PassesResult())
    }
  }

  override def loadOptionsFrom(
    configFile: Path,
    log: Logger, 
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, log, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
