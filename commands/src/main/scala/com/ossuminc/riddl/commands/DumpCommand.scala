/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Logger, StringHelpers}
import com.ossuminc.riddl.command.Command

import java.nio.file.Path

object DumpCommand {
  final val cmdName = "dump"
}

/** A Command for Parsing RIDDL input
  */
class DumpCommand extends InputFileCommand(DumpCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      val rpi = RiddlParserInput.fromCwdPath(inputFile)
      Riddl.parseAndValidate(rpi, commonOptions).map { result =>
        log.info(s"AST of $inputFile is:")
        log.info(StringHelpers.toPrettyString(result, 1, None))
        result
      }
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
