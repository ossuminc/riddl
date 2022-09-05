package com.reactific.riddl.commands

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.{CommonOptions, Riddl}
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.StringHelpers.toPrettyString

import java.nio.file.Path

/**
 * A Command for Parsing RIDDL input
 */
class DumpCommand extends InputFileCommandPlugin("dump") {
  import InputFileCommandPlugin.Options
  def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages,Unit] = {
    options.withInputFile { (inputFile: Path) =>
      Riddl.parseAndValidate(inputFile, commonOptions).map { root =>
        toPrettyString(root,1,
          Some(s"Parsing inputFile yielded:${System.lineSeparator()}"))
        ()
      }
    }
  }

  override def replaceInputFile(
    opts: Options, inputFile: Path
  ): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def loadOptionsFrom(configFile: Path, commonOptions: CommonOptions):
  Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
