/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.passes.{PassesResult, Riddl}
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.StringHelpers.toPrettyString

import java.nio.file.Path

object DumpCommand {
  final val cmdName = "dump"
}

/** A Command for Parsing RIDDL input
  */
class DumpCommand extends InputFileCommandPlugin(DumpCommand.cmdName) {
  import InputFileCommandPlugin.Options

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      Riddl.parseAndValidate(inputFile, commonOptions).map { result =>
        log.info(s"AST of $inputFile is:")
        log.info(toPrettyString(result, 1, None))
        result
      }
    }
  }

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
