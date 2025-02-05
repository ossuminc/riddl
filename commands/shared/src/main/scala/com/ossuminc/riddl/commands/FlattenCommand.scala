/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.commands.InputFileCommand.Options
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.utils.{Await, PlatformContext}
import org.ekrich.config.Config
import scopt.OParser

import java.io.File
import java.nio.file.Path
import scala.+:
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object FlattenCommand {
  final val cmdName = "flatten"
  case class Options(inputFile: Option[Path]) extends CommandOptions with PassOptions:
    def command: String = cmdName
  end Options
}

/** A Command for Parsing RIDDL input
  */
class FlattenCommand(using pc: PlatformContext) extends Command[FlattenCommand.Options](FlattenCommand.cmdName) {

  import FlattenCommand.Options

  def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName).children(
      arg[File]("input-file").action((f, opt) => opt.copy(inputFile = Some(f.toPath)))
    ) -> FlattenCommand.Options(None)
  }

  override def interpretConfig(config: Config) =
    val obj = config.getObject(commandName).toConfig
    val inputFile = Some(Path.of(obj.getString("input-file")))
    Options(inputFile)
  end interpretConfig

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile[PassesResult] { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
        Riddl.parse(rpi) match
          case Left(errors) => return Left(errors)
          case Right(root) =>
            val input = PassInput(root)
            val passes: Seq[PassCreator] = Pass.standardPasses.appended(FlattenPass.creator(options))
            Right(Pass.runThesePasses(input, passes))
        end match
      }
      Await.result(future, 10.seconds)
    }
  }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      val ifco = resolveInputFileToConfigFile(options, configFile)
      Options(ifco.inputFile)
    }
  }
}
