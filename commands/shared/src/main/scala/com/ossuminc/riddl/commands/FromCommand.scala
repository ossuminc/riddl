/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, StringHelpers}
import org.ekrich.config.*
import scopt.OParser

import java.io.File
import java.nio.file.Path

/** Unit Tests For FromCommand */
object FromCommand {
  final val cmdName = "from"
  case class Options(inputFile: Option[Path] = None, targetCommand: String = "") extends CommandOptions {
    def command: String = cmdName
  }
}

class FromCommand(using val pc: PlatformContext) extends Command[FromCommand.Options](FromCommand.cmdName) {
  import FromCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(FromCommand.cmdName)
      .children(
        arg[File]("config-file")
          .action { (file, opt) =>
            opt.copy(inputFile = Some(file.toPath))
          }
          .text("A HOCON configuration file with riddlc options in it."),
        arg[String]("target-command")
          .action { (cmd, opt) =>
            opt.copy(targetCommand = cmd)
          }
          .text("The name of the command to select from the configuration file")
      )
      .text("Loads a configuration file and executes the command in it") ->
      FromCommand.Options()
  }

  override def interpretConfig(config: Config): FromCommand.Options =
    val rootObj = config.getObject(commandName)
    val rootConfig = rootObj.toConfig 
    val inputFile = 
      if rootObj.containsKey("config-file") then
        Some(Path.of(rootConfig.getString("config-file")))
      else
        None
    val targetCommand = rootConfig.getString("target-command")
    Options(inputFile, targetCommand)
  end interpretConfig

  override def run(
    options: FromCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val loadedCO =
      CommonOptionsHelper.loadCommonOptions(options.inputFile.fold(Path.of(""))(identity)) match
        case Right(newCO: CommonOptions) =>
          if pc.options.verbose then
            pc.log.info(
              s"Read new common options from ${options.inputFile.get} as:\n" +
                StringHelpers.toPrettyString(newCO)
            )
          newCO
        case Left(messages) =>
          if pc.options.debug then
            pc.stdout(
              s"Failed to read common options from ${options.inputFile.get} because:\n" ++
                messages.format
            )
          end if
          pc.options
      end match
      
    pc.withOptions(loadedCO) { _ =>
      Commands.runFromConfig(
        options.inputFile,
        options.targetCommand,
        "from"
      )
    }
  }

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
