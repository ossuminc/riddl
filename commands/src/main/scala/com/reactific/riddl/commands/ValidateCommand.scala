/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.annotation.unused

/** Validate Command */
class ValidateCommand extends InputFileCommandPlugin("validate") {
  import InputFileCommandPlugin.Options
  override def run(
    options: Options,
    @unused commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    options.withInputFile { inputFile: Path =>
      Riddl.parseAndValidate(inputFile, commonOptions).map(_ => ())
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
