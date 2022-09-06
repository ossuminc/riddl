package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.{Logger, RiddlBuildInfo}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object VersionCommand {
  case class Options(
    command: String = "version",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
  ) extends CommandOptions
}

class VersionCommand
  extends CommandPlugin[VersionCommand.Options]("version") {
  import VersionCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action((_, c) => c.copy(command = pluginName))
      .text("Print the version of riddlc and exits")
      -> VersionCommand.Options()
  }

  override def getConfigReader:
  ConfigReader[VersionCommand.Options] = { (cur: ConfigCursor) =>
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
    options: VersionCommand.Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    if (commonOptions.verbose || !commonOptions.quiet) {
      println(RiddlBuildInfo.version)
    }
    Right(())
  }
}
