/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, CommonOptions, Logger, PlatformContext, StringHelpers, URL}
import com.ossuminc.riddl.utils.{pc, ec}

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

object FlattenCommand {
  final val cmdName = "flatten"
}

/** A Command for Parsing RIDDL input
  */
class FlattenCommand(using io: PlatformContext) extends InputFileCommand(DumpCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
        Riddl.parseAndValidate(rpi).map { result =>
          // TODO: output the model to System.out without spacing and with a line break only after every Definition
          result
        }
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
