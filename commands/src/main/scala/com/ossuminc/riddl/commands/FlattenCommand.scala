/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{PlatformIOContext, Logger, StringHelpers}

import java.nio.file.Path

object FlattenCommand {
  final val cmdName = "flatten"
}

/** A Command for Parsing RIDDL input
  */
class FlattenCommand(using io: PlatformIOContext) extends InputFileCommand(DumpCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      val rpi = RiddlParserInput.fromCwdPath(inputFile)
      Riddl.parseAndValidate(rpi, commonOptions).map { result =>
        // TODO: output the model to System.out without spacing and with a line break only after every Definition
        result
      }
    }
  }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
