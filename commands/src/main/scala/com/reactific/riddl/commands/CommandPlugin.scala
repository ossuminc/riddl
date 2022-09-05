package com.reactific.riddl.commands

import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.{Logger, Plugin, PluginInterface, RiddlBuildInfo}
import pureconfig.{ConfigReader, ConfigSource}
import scopt.{OParser, OParserBuilder}

import java.io.File
import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.{ClassTag, classTag}

object CommandPlugin {
  def loadCommandNamed(
    name: String,
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, CommandPlugin[CommandOptions]] = {
    val loaded = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]](pluginsDir)
    if (loaded.isEmpty) {
      Left(errors(s"No command plugins loaded from: $pluginsDir"))
    } else {
      loaded.find(_.pluginName == name) match {
        case Some(pl) if pl.isInstanceOf[CommandPlugin[CommandOptions]] =>
          Right(pl)
        case Some(plugin) =>
          Left(errors(
            s"Plugin for command $name is the wrong type ${
              plugin.getClass.getSimpleName
            }"
          ))
        case None => Left(errors(s"No plugin command matches '$name'"))
      }
    }
  }

  def runCommandWithArgs(
    name: String,
    args: Array[String],
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, Unit]= {
    loadCommandNamed(name, pluginsDir).map { cmd =>
      cmd.run(args, commonOptions, log)
    }
  }

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, CommandPlugin[CommandOptions]] = {
    for {
      cmd <- loadCommandNamed(name, pluginsDir)
      opts <- cmd.loadOptionsFrom(optionsPath)
      _ <- cmd.run(opts, commonOptions, log)
    } yield {
      require(opts.getClass == cmd.optionsClass)
      cmd
    }
  }

  def loadCandidateCommands(configFile: Path): Either[Messages, Seq[String]] = {
    val names = ConfigSource.file(configFile.toFile).value()
      .map(_.keySet().asScala.toSeq)
    names match {
      case Right(value) => Right(value)
      case Left(fails) => Left(
          errors(
            s"Errors while reading $configFile:\n" +
              fails.prettyPrint(1)
          )
        )
    }
  }

  def runFromConfig(
    configFile: Option[Path],
    targetCommand: Option[String],
    commonOptions: CommonOptions,
    log: Logger,
    commandName: String
  ): Either[Messages, Unit] = {
    CommandOptions.withInputFile(configFile,commandName) { path =>
      CommandPlugin.loadCandidateCommands(path).map { names =>
        targetCommand match {
          case None =>
            val messages = names.foldLeft(Messages.empty) { (m, name) =>
              CommandPlugin.loadCommandNamed(name).map { cmd =>
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

/** The service interface for Riddlc command plugins */
abstract class CommandPlugin[OPT <: CommandOptions : ClassTag](
  val pluginName: String
) extends PluginInterface {
  final override def pluginVersion: String = RiddlBuildInfo.version

  val optionsClass: Class[?] = classTag[OPT].runtimeClass

  /**
   * Provide an scopt OParser for the commands options type, OPT
   * @param log A logger to use for output (discouraged)
   * @return A pair: the OParser and the default values for OPT
   */
  def getOptions(): (OParser[Unit,OPT], OPT)

  def parseOptions(
    args: Array[String]
  ): Option[OPT] = {
    val (parser, default) = getOptions()
    val (result, effects) = OParser.runParser(parser, args, default)
    OParser.runEffects(effects)
    result
  }

  /**
   * Provide a typesafe/Config reader for the commands options. This
   * reader should read an object having the same name as the command. The fields
   * of that object must correspond to the fields of the OPT type.
   * @param log A logger to use for output (discouraged)
   * @return A pureconfig.ConfigReader[OPT] that knows how to read OPT
   */
  def getConfigReader() : ConfigReader[OPT]

  def loadOptionsFrom(configFile: Path): Either[Messages,OPT] = {
    ConfigSource.file(configFile).load[OPT](getConfigReader()) match {
      case Right(value) => Right(value)
      case Left(fails) => Left(errors(
        "Errors while reading ${configFile}:\n" + fails.prettyPrint(1)
      ))
    }
  }

  def runFrom(
    configFile: Path,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    loadOptionsFrom(configFile)
      .flatMap(run(_,commonOptions,log))
  }

/**
   * Execute the command given the options. Error should be returned as
   * Left(messages) and not directly logged. The log is for verbose or debug
   * output
   * @param options The command specific options
   * @param commonOptions The options common to all commands
   * @param log A logger for logging errors, warnings, and info
   * @return Either a set of Messages on error or a Unit on success
   */
  def run(
    options: OPT,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages,Unit]

  def run(
    args : Array[String],
    commonOptions: CommonOptions,
    log: Logger
  ) : Either[Messages,Unit] = {
    val maybeOptions: Option[OPT] = parseOptions(args)
    maybeOptions match {
      case Some(opts: OPT) =>
        run(opts, commonOptions, log)
      case Some(_) =>
        Left(errors(s"Failed to match option type ${
          optionsClass.getSimpleName}"))
      case None =>
        Left(errors(s"Failed to parse $pluginName options"))
    }
  }

  type OptionPlacer[V] = (V, OPT) => OPT
  protected val builder: OParserBuilder[OPT] = OParser.builder[OPT]
  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, OPT] = {
    opt[File]('i', "input-file").optional().action((v, c) => f(v, c))
      .text("required riddl input file to read")
  }

  def outputDir(f: OptionPlacer[File]): OParser[File, OPT] = {
    opt[File]('o', "output-dir").optional().action((v, c) => f(v, c))
      .text("required output directory for the generated output")
  }

}

