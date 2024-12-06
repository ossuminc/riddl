/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.PlatformContext
import org.ekrich.config.*
import scopt.OParser

import java.nio.file.Path

/** Implementation of the */
object AboutCommand {
  case class Options(command: String = "about")
      extends CommandOptions {
    override def inputFile: Option[Path] = None
  }
}

class AboutCommand(using io: PlatformContext) extends Command[AboutCommand.Options]("about") {
  import AboutCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print out information about RIDDL") -> AboutCommand.Options()
  }

  override def interpretConfig(config: Config): AboutCommand.Options =
    val obj = config.getObject(commandName)
    Options(commandName)

  override def run(
    options: AboutCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if io.options.verbose || !io.options.quiet then {
      val about: String = {
        CommonOptionsHelper.blurb ++ System.lineSeparator() ++
          "Extensive Documentation here: https://riddl.tech"
      }

      io.log.info(about)
    }
    Right(PassesResult())
  }
}
