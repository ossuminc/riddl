package com.reactific.riddl

import com.reactific.riddl.commands.CommandOptions.commandOptionsParser
import com.reactific.riddl.commands.CommonOptionsHelper.commonOptionsParser
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser
import scopt.RenderingMode.OneColumn

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
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName).action((_, c) => c.copy(command = pluginName))
      .text("Print out how to use this program")
      -> HelpCommand.Options()
  }

  override def getConfigReader:
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
    if (commonOptions.verbose || !commonOptions.quiet) {
        val usage: String = {
        val common = OParser.usage(commonOptionsParser, OneColumn)
        val commands = OParser.usage(commandOptionsParser, OneColumn)
        val improved_commands = commands.split(System.lineSeparator())
          .flatMap { line =>
            if (line.isEmpty || line.forall(_.isWhitespace)) {
              Seq.empty[String]
            } else if (line.startsWith("Command:")) {
              Seq(System.lineSeparator() + line)
            } else if (line.startsWith("Usage:")) { Seq(line) }
            else { Seq("  " + line) }
          }.mkString(System.lineSeparator())
        common ++ "\n\n" ++ improved_commands
      }
      println(usage)
    }
    Right(())
  }
}
