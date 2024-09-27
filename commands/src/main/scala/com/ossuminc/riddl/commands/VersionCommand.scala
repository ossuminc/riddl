/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.Logger
import com.ossuminc.riddl.utils.RiddlBuildInfo

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path
import com.ossuminc.riddl.command.{Command, CommandOptions}

/** Unit Tests For FromCommand */
object VersionCommand {
  case class Options(command: String = "version", inputFile: Option[Path] = None, targetCommand: Option[String] = None)
      extends CommandOptions
}

class VersionCommand extends Command[VersionCommand.Options]("version") {
  import VersionCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action((_, c) => c.copy(command = pluginName))
      .text("Print the version of riddlc and exits") -> VersionCommand.Options()
  }

  override def getConfigReader: ConfigReader[VersionCommand.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      cmd <- topRes.asObjectCursor
    yield { Options(cmd.path) }
  }

  override def run(
    options: VersionCommand.Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if commonOptions.verbose || !commonOptions.quiet then {
      log.info(RiddlBuildInfo.version)
    }
    Right(PassesResult())
  }
}
