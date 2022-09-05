package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object HelpCommand {
  case class Options(
    command: String = "help",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
  ) extends CommandOptions
}

class HelpCommand extends CommandPlugin[HelpCommand.Options]("help") {
  import HelpCommand.Options
  override def getOptions(): (OParser[Unit, Options], Options) = {
    import builder._
    cmd("help").action((_, c) => c.copy(command = "help"))
      .text("Print out how to use this program")
      -> HelpCommand.Options()
  }

  override def getConfigReader():
  ConfigReader[HelpCommand.Options] = { (cur: ConfigCursor) =>
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
    options: HelpCommand.Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    println(RiddlOptions.usage)
    Right(())
  }
}
