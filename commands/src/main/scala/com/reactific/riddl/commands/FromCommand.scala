package com.reactific.riddl.commands
import com.reactific.riddl.commands.CommandPlugin.loadCommandNamed
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

/** Unit Tests For FromCommand */
object FromCommand {
  case class Options(
    command: Command = PluginCommand("from"),
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
  ) extends CommandOptions
}

class FromCommand extends CommandPlugin[FromCommand.Options]("from") {
  import FromCommand.Options
  override def getOptions(log: Logger): (OParser[Unit, Options], Options) = {
    import builder._
    cmd("from")
      .action((_, c) => c.copy(command = PluginCommand(pluginName)))
      .children(
        arg[File]("config-file")
          .action { (file, opt) => opt.copy(inputFile = Some(file.toPath))}
          .text("A HOCON configuration file with riddlc options in it."),
        opt[Option[String]]("target-command")
          .action { (cmd, opt) => opt.copy(targetCommand = cmd) }
          .text("The name of the command to select from the configuration file")
      )
      .text("Loads a configuration file and executes the command in it")
      -> FromCommand.Options()
  }

  override def getConfigReader(
    log: Logger
  ): ConfigReader[FromCommand.Options] = { (cur: ConfigCursor) =>
    for {
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inFileRes <- objCur.atKey("config-file").map(_.asString)
      inFile <- inFileRes
      target <- optional[Option[String]](objCur, "target-command", None){
        cc => cc.asString.map(Option(_))
      }
    } yield {
      Options(
        inputFile = Some(Path.of(inFile)),
        targetCommand = target
      )
    }
  }


  override def run(
    options: FromCommand.Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    options.withInputFile { path =>
      CommandPlugin.loadCandidateCommands(path).map { names =>
        options.targetCommand match {
          case None =>
            val messages = names.foldLeft(Messages.empty) { (m, name) =>
              loadCommandNamed(name).map { cmd =>
                cmd.runFrom(path, commonOptions, log)
              } match {
                case Right(_) => m ++ Messages.empty
                case Left(msgs) => m ++ msgs
              }
            }
            if (messages.isEmpty) {
              Right(())
            } else {
              Left(messages)
            }
          case Some(cmd) =>
            if (names.contains(cmd)) {
              CommandPlugin.runCommandNamed(cmd, path, log, commonOptions)
            } else {
              Left(errors(s"Command '$cmd' is not defined in $path"))
            }
        }
      }
    }
  }
}
