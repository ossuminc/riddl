/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.commands.CommandOptions
import com.ossuminc.riddl.commands.CommandPlugin
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{Logger,RiddlBuildInfo}

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object InfoCommand {
  case class Options(
    command: String = "info",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None)
      extends CommandOptions
}

class InfoCommand extends CommandPlugin[InfoCommand.Options]("info") {
  import InfoCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName).action((_, c) => c.copy(command = pluginName))
      .text("Print out build information about this program") ->
      InfoCommand.Options()
  }

  override def getConfigReader: ConfigReader[InfoCommand.Options] = {
    (cur: ConfigCursor) =>
      for
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(pluginName)
        cmd <- topRes.asObjectCursor
      yield { Options(cmd.path) }
  }

  override def run(
    options: InfoCommand.Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    log.info("About riddlc:")
    log.info(s"           name: riddlc")
    log.info(s"        version: ${RiddlBuildInfo.version}")
    log.info(s"  documentation: https://riddl.tech")
    log.info(s"      copyright: ${RiddlBuildInfo.copyright}")
    log.info(s"       built at: ${RiddlBuildInfo.builtAtString}")
    log.info(s"       licenses: ${RiddlBuildInfo.licenses}")
    log.info(s"   organization: ${RiddlBuildInfo.organizationName}")
    log.info(s"  scala version: ${RiddlBuildInfo.scalaVersion}")
    log.info(s"    sbt version: ${RiddlBuildInfo.sbtVersion}")
    log.info(s"       jvm name: ${System.getProperty("java.vm.name")}")
    log.info(s"    jvm version: ${System.getProperty("java.runtime.version")}")
    log.info(s"  operating sys: ${System.getProperty("os.name")}")
    Right(PassesResult())
  }
}
