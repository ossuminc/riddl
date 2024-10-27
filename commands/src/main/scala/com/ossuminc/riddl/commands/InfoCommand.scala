/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Logger, RiddlBuildInfo}
import com.ossuminc.riddl.utils.{pc, ec}

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object InfoCommand {
  case class Options(command: String = "info", inputFile: Option[Path] = None, targetCommand: Option[String] = None)
      extends CommandOptions
}

class InfoCommand(using io: PlatformContext) extends Command[InfoCommand.Options]("info") {
  import InfoCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action((_, c) => c.copy(command = pluginName))
      .text("Print out build information about this program") ->
      InfoCommand.Options()
  }

  override def getConfigReader: ConfigReader[InfoCommand.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      cmd <- topRes.asObjectCursor
    yield { Options(cmd.path) }
  }

  override def run(
    options: InfoCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    io.log.info("About riddlc:")
    io.log.info(s"           name: riddlc")
    io.log.info(s"        version: ${RiddlBuildInfo.version}")
    io.log.info(s"  documentation: https://riddl.tech")
    io.log.info(s"      copyright: ${RiddlBuildInfo.copyright}")
    io.log.info(s"       built at: ${RiddlBuildInfo.builtAtString}")
    io.log.info(s"       licenses: ${RiddlBuildInfo.licenses}")
    io.log.info(s"   organization: ${RiddlBuildInfo.organizationName}")
    io.log.info(s"  scala version: ${RiddlBuildInfo.scalaVersion}")
    io.log.info(s"    sbt version: ${RiddlBuildInfo.sbtVersion}")
    io.log.info(s"       jvm name: ${System.getProperty("java.vm.name")}")
    io.log.info(s"    jvm version: ${System.getProperty("java.runtime.version")}")
    io.log.info(s"  operating sys: ${System.getProperty("os.name")}")
    Right(PassesResult())
  }
}
