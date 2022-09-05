package com.reactific.riddl.commands

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.{CommonOptions, Riddl}
import com.reactific.riddl.utils.Logger

import java.nio.file.Path

/**
 * A Command for Parsing RIDDL input
 */
class ParseCommand extends InputFileCommandPlugin("parse") {
  import InputFileCommandPlugin.Options
  def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages,Unit] = {
    options.withInputFile { (inputFile: Path) =>
      Riddl.parse(inputFile, commonOptions).map(_ => ())
    }
  }
}
