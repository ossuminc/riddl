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

object ParseCommand {
  val cmdName = "parse"
}

/** A Command for Parsing RIDDL input
  */
class ParseCommand extends InputFileCommandPlugin(ParseCommand.cmdName) {
  import InputFileCommandPlugin.Options
  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    options.withInputFile { (inputFile: Path) =>
      Riddl.parse(inputFile, commonOptions).map(_ => ())
    }
  }
}
