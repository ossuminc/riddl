/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.utils.PlatformContext
import org.ekrich.config.*
import scopt.OParser

import java.io.File
import java.nio.file.Path

object InputFileCommand {
  case class Options(inputFile: Option[Path] = None, command: String = "unspecified") extends CommandOptions
}

/** An abstract command definition helper class for commands that only take a single input file parameter
  * @param name
  *   The name of the command
  */
abstract class InputFileCommand(name: String)(using io: PlatformContext)
    extends Command[InputFileCommand.Options](name):
  import InputFileCommand.Options
  def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(name).children(
      arg[File]("input-file").action((f, opt) => opt.copy(command = name, inputFile = Some(f.toPath)))
    ) -> InputFileCommand.Options()
  }

  override def interpretConfig(config: Config): InputFileCommand.Options =
    val rootObj = config.getObject(commandName).toConfig
    Options(Some(Path.of(rootObj.getString("input-file"))), commandName)
  end interpretConfig
end InputFileCommand
