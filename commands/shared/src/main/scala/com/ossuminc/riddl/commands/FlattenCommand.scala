/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.utils.{Await, PlatformContext}

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object FlattenCommand {
  final val cmdName = "flatten"
}

/** A Command for Parsing RIDDL input
  */
class FlattenCommand(using pc: PlatformContext) extends InputFileCommand(DumpCommand.cmdName) {
  import InputFileCommand.Options

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
        Riddl.parse(rpi)(pc) match
          case Left(errors) => return Left(errors)
          case Right(root) =>
            val input = PassInput(root)
            val result = Pass.runThesePasses(input, Pass.standardPasses +: FlattenPass.creator )

        end match
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
