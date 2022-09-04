package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.{Logger, Plugin, PluginInterface, RiddlBuildInfo}
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigCursor, ConfigObjectCursor, ConfigReader, ConfigSource}
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
        case Some(pl) if pl.isInstanceOf[CommandPlugin[CommandOptions]] => Right(pl)
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

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, Unit] = {
    loadCommandNamed(name, pluginsDir).map { cmd =>
      cmd.loadOptionsFrom(optionsPath, log).map { opts: CommandOptions =>
        require(opts.getClass == cmd.optionsClass)
        cmd.run(opts, commonOptions, log)
      }
    }
  }

  def loadCandidateCommands(configFile: Path): Either[Messages, Seq[String]] = {
    val names = ConfigSource.file(configFile.toFile).value()
      .map(_.keySet().asScala.toSeq)
    names match {
      case Right(value) => Right(value)
      case Left(fails) => Left(
          errors(
            s"Errors while reading ${configFile}:\n" +
              fails.prettyPrint(1)
          )
        )
    }
  }


}

/** The service interface for Riddlc command plugins */
abstract class CommandPlugin[OPT <: CommandOptions : ClassTag](
  val pluginName: String
) extends PluginInterface {
  final override def pluginVersion: String = RiddlBuildInfo.version

  val optionsClass = classTag[OPT].runtimeClass

  /**
   * Provide an scopt OParser for the commands options type, OPT
   * @param log A logger to use for output (discouraged)
   * @return A pair: the OParser and the default values for OPT
   */
  def getOptions(log: Logger): (OParser[Unit,OPT], OPT)

  /**
   * Provide a typesafe/Config reader for the commands options. This
   * reader should read an object having the same name as the command. The fields
   * of that object must correspond to the fields of the OPT type.
   * @param log A logger to use for output (discouraged)
   * @return A [[pureconfig.ConfigReader[OPT]] that knows how to read [[OPT]]
   */
  def getConfigReader(log: Logger) : ConfigReader[OPT]

  final def loadOptionsFrom(configFile: Path, log: Logger): Either[Messages,OPT] = {
    ConfigSource.file(configFile).load[OPT](getConfigReader(log)) match {
      case Right(value) => Right(value)
      case Left(fails) => Left(errors(
        "Errors while reading ${configFile}:\n" + fails.prettyPrint(1)
      ))
    }
  }

  final def runFrom(
    configFile: Path,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    loadOptionsFrom(configFile, log)
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

  /** A helper function for reading optional items from a config file.
   *
   * @param objCur The ConfigObjectCursor to start with
   * @param key The name of the optional config item
   * @param default The default value of the config item
   * @param mapIt The function to map ConfigCursor to ConfigReader.Result[T]
   * @tparam T The Scala type of the config item's value
   * @return The reader for this optional configuration item.
   */
  protected def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T
  )(mapIt: ConfigCursor => ConfigReader.Result[T]
  ): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur                      => mapIt(stCur)
    }
  }

  /**
   * Make a readable set of error messages from a ConfigReaderFailures object
   * @param errors The errors to convert
   * @return A sequence of strings that are suitable for output
   */
  def stringifyConfigReaderErrors(errors: ConfigReaderFailures): String = {
    errors.prettyPrint(1)
    /*
    errors.toList.map { crf =>
      val location = crf.origin match {
        case Some(origin) => origin.description
        case None         => "unknown location"
      }
      s"At $location: ${crf.description}"
    }
    */
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

  implicit val coReader: ConfigReader[CommonOptions] = { (cur: ConfigCursor) =>
    for {
      objCur <- cur.asObjectCursor
      showTimes <- optional(objCur, "show-times", false)(cc => cc.asBoolean)
      verbose <- optional(objCur, "verbose", false)(cc => cc.asBoolean)
      quiet <- optional(objCur, "quiet", false)(cc => cc.asBoolean)
      dryRun <- optional(objCur, "dry-run", false)(cc => cc.asBoolean)
      debug <- optional(objCur, "debug", false)(cc => cc.asBoolean)
      showWarnings <- optional(objCur, "show-warnings", true) { cc =>
        cc.asBoolean
      }
      showStyleWarnings <-
        optional(objCur, "show-style-warnings", false) { cc => cc.asBoolean }
      showMissingWarnings <-
        optional(objCur, "show-missing-warnings", false) { cc =>
          cc.asBoolean
        }
    } yield {
      CommonOptions(
        showTimes,
        verbose,
        dryRun,
        quiet,
        showWarnings,
        showMissingWarnings,
        showStyleWarnings,
        debug
      )
    }
  }
}

