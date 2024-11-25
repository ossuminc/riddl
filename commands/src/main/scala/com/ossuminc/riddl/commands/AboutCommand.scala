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
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

/** Implementation of the */
object AboutCommand {
  case class Options(command: String = "about", inputFile: Option[Path] = None, targetCommand: Option[String] = None)
      extends CommandOptions
}

class AboutCommand(using io: PlatformContext) extends Command[AboutCommand.Options]("about") {
  import AboutCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action((_, c) => c.copy(command = pluginName))
      .text("Print out information about RIDDL") -> AboutCommand.Options()
  }

  override def getConfigReader: ConfigReader[AboutCommand.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      cmd <- topRes.asObjectCursor
    yield { Options(cmd.path) }
  }

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
