/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext, StringHelpers}

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object DumpCommand {
  final val cmdName = "dump"
}

/** A Command for Parsing RIDDL input
  */
class DumpCommand(using pc: PlatformContext) extends InputFileCommand(DumpCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
        Riddl.parseAndValidate(rpi).map { result =>
          pc.log.info(s"AST of $inputFile is:")
          pc.log.info(StringHelpers.toPrettyString(result, 1, None))
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
