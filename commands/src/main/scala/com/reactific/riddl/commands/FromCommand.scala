package com.reactific.riddl.commands
import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

/** Unit Tests For FromCommand */
object FromCommand {
  case class Options(
    command: String = "from",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
  ) extends CommandOptions
}

class FromCommand extends CommandPlugin[FromCommand.Options]("from") {
  import FromCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd("from")
      .action((_, c) => c.copy(command = pluginName))
      .children(
        arg[File]("config-file")
          .action { (file, opt) => opt.copy(inputFile = Some(file.toPath))}
          .text("A HOCON configuration file with riddlc options in it."),
        arg[Option[String]]("target-command")
          .optional()
          .action { (cmd, opt) => opt.copy(targetCommand = cmd) }
          .text("The name of the command to select from the configuration file")
      )
      .text("Loads a configuration file and executes the command in it")
      -> FromCommand.Options()
  }

  override def getConfigReader:
  ConfigReader[FromCommand.Options] = { (cur: ConfigCursor) =>
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
    CommandPlugin.runFromConfig(
      options.inputFile, options.targetCommand, commonOptions, log, pluginName
    )
  }

  override def replaceInputFile(
    opts: Options, inputFile: Path
  ): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def loadOptionsFrom(configFile: Path, commonOptions: CommonOptions):
  Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
