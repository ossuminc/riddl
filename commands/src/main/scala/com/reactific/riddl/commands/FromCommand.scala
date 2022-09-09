package com.reactific.riddl.commands
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.{Logger, StringHelpers}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

/** Unit Tests For FromCommand */
object FromCommand {
  case class Options(
    inputFile: Option[Path] = None,
    targetCommand: String = ""
  ) extends CommandOptions {
    def command: String = "from"
  }
}

class FromCommand extends CommandPlugin[FromCommand.Options]("from") {
  import FromCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd("from")
      .children(
        arg[File]("config-file")
          .action { (file, opt) => opt.copy(inputFile = Some(file.toPath))}
          .text("A HOCON configuration file with riddlc options in it."),
        arg[String]("target-command")
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
      targetRes <- objCur.atKey("target-command").map(_.asString)
      target <- targetRes
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
    val loadedCO =
      CommandOptions.loadCommonOptions(options.inputFile.get) match {
        case Right(newCO: CommonOptions) =>
          if (commonOptions.verbose) {
            println(s"Read new common options from ${
              options.inputFile.get} as:\n" +
              StringHelpers.toPrettyString(newCO))
          }
          newCO
        case Left(messages) =>
          if (commonOptions.debug) {
            println(s"Failed to read common options from ${
              options.inputFile.get} because:\n" ++ messages.format)
          }
          commonOptions
    }
    val result = CommandPlugin.runFromConfig(
      options.inputFile, options.targetCommand, loadedCO, log, pluginName)
    result
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
