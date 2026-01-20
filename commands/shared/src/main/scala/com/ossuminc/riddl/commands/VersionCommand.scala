/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{PlatformContext, RiddlBuildInfo}
import org.ekrich.config.*
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object VersionCommand {
  case class Options(command: String) extends CommandOptions:
    def inputFile: Option[Path] = None
  end Options
}

class VersionCommand(using pc: PlatformContext) extends Command[VersionCommand.Options]("version") {
  import VersionCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print the version of riddlc and exits") -> VersionCommand.Options(commandName)
  }

  override def interpretConfig(config: Config): Options =
    Options(commandName)
  end interpretConfig


  override def run(
    options: VersionCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if pc.options.verbose || !pc.options.quiet then {
      pc.log.info(RiddlBuildInfo.version)
    }
    Right(PassesResult())
  }
}
