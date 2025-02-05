/*
 * Copyright 2019-2025 Ossum, Inc.
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
object InfoCommand:
  case class Options(command: String = "info") extends CommandOptions:
    def inputFile: Option[Path] = None
  end Options
end InfoCommand

class InfoCommand(using pc: PlatformContext) extends Command[InfoCommand.Options]("info") {
  import InfoCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print out build information about this program") ->
      InfoCommand.Options()
  }

  override def interpretConfig(config: Config) =
    Options(commandName)
  end interpretConfig

  override def run(
    options: InfoCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    pc.log.info("About riddlc:")
    pc.log.info(s"           name: riddlc")
    pc.log.info(s"        version: ${RiddlBuildInfo.version}")
    pc.log.info(s"  documentation: https://riddl.tech")
    pc.log.info(s"      copyright: ${RiddlBuildInfo.copyright}")
    pc.log.info(s"       built at: ${RiddlBuildInfo.builtAtString}")
    pc.log.info(s"       licenses: ${RiddlBuildInfo.licenses}")
    pc.log.info(s"   organization: ${RiddlBuildInfo.organizationName}")
    pc.log.info(s"  scala version: ${RiddlBuildInfo.scalaVersion}")
    pc.log.info(s"    sbt version: ${RiddlBuildInfo.sbtVersion}")
    pc.log.info(s"       jvm name: ${System.getProperty("java.vm.name")}")
    pc.log.info(s"    jvm version: ${System.getProperty("java.runtime.version")}")
    pc.log.info(s"  operating sys: ${System.getProperty("os.name")}")
    Right(PassesResult())
  }
}
