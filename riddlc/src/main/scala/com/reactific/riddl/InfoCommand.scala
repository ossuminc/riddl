package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.{Logger, RiddlBuildInfo}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object InfoCommand {
  case class Options(
    command: String = "info",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
  ) extends CommandOptions
}

class InfoCommand extends CommandPlugin[InfoCommand.Options]("info") {
  import InfoCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName).action((_, c) => c.copy(command = pluginName))
      .text("Print out build information about this program")
      -> InfoCommand.Options()
  }

  override def getConfigReader:
  ConfigReader[InfoCommand.Options] = { (cur: ConfigCursor) =>
    for {
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      cmd <- topRes.asString
    } yield {
      Options(
        cmd,
        inputFile = None,
        targetCommand = None
      )
    }
  }

  override def run(
    options: InfoCommand.Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
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
    Right(())
  }
}
