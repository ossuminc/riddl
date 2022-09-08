package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Messages.SevereError
import com.reactific.riddl.language.Messages.errors
import com.reactific.riddl.language.Messages.highestSeverity
import com.reactific.riddl.language.Messages.severes
import com.reactific.riddl.utils.StringHelpers.toPrettyString
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.Plugin
import com.reactific.riddl.utils.PluginInterface
import com.reactific.riddl.utils.RiddlBuildInfo
import com.reactific.riddl.utils.StringLogger
import com.reactific.riddl.utils.SysLogger
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import scopt.OParser
import scopt.OParserBuilder

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.control.NonFatal

object CommandPlugin {
  def loadCommandNamed(
    name: String,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, CommandPlugin[CommandOptions]] = {
    if (commonOptions.verbose) { println(s"Loading command: $name") }
    val loaded = Plugin
      .loadPluginsFrom[CommandPlugin[CommandOptions]](pluginsDir)
    if (loaded.isEmpty) { Left(errors(s"No command found for '$name'")) }
    else {
      loaded.find(_.pluginName == name) match {
        case Some(pl) if pl.isInstanceOf[CommandPlugin[CommandOptions]] =>
          Right(pl)
        case Some(plugin) => Left(errors(
            s"Plugin for command $name is the wrong type ${plugin.getClass.getSimpleName}"
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
  ): Either[Messages, Unit] = {
    val result = loadCommandNamed(name, commonOptions, pluginsDir)
      .flatMap { cmd => cmd.run(args, commonOptions, log) }
    result
  }

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, CommandPlugin[CommandOptions]] = {
    if (commonOptions.verbose) {
      println(s"About to run $name with options from $optionsPath")
    }
    loadCommandNamed(name, commonOptions, pluginsDir).flatMap { cmd =>
      cmd.loadOptionsFrom(optionsPath, commonOptions).flatMap { opts =>
        cmd.run(opts, commonOptions, log).map(_ => cmd)
      }
    }
  }

  def loadCandidateCommands(
    configFile: Path,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, Seq[String]] = {
    val names = ConfigSource.file(configFile.toFile).value()
      .map(_.keySet().asScala.toSeq)
    names match {
      case Right(value) =>
        if (commonOptions.verbose) {
          println(
            s"Found candidate commands in $configFile: ${value.mkString(" ")}"
          )
        }
        Right(value)
      case Left(fails) =>
        val message = s"Errors while reading $configFile:\n" +
          fails.prettyPrint(1)
        Left(errors(message))
    }
  }

  def runFromConfig(
    configFile: Option[Path],
    targetCommand: String,
    commonOptions: CommonOptions,
    log: Logger,
    commandName: String
  ): Either[Messages, Unit] = {
    val result = CommandOptions.withInputFile(configFile, commandName) { path =>
      val candidate = CommandPlugin.loadCandidateCommands(path, commonOptions)
        .flatMap { names =>
          if (names.contains(targetCommand)) {
            CommandPlugin
              .runCommandNamed(targetCommand, path, log, commonOptions)
              .map(_ => ())
          } else {
            Left[Messages, Unit](errors(
              s"Command '$targetCommand' is not defined in $path"
            ))
          }
        }
      candidate
    }
    result
  }

  def runMain(args: Array[String]): Int = {
    var log: Logger = SysLogger()

    try {
      val (common, remaining) = com.reactific.riddl.commands.CommonOptionsHelper
        .parseCommonOptions(args)
      common match {
        case Some(commonOptions) =>
          if (remaining.isEmpty) {
            if (!commonOptions.quiet) {
              log.error("No command argument was provided")
            }
            1
          } else {
            val name = remaining.head
            if (commonOptions.dryRun) {
              if (!commonOptions.quiet) {
                log.info(s"Would have executed: ${remaining.mkString(" ")}")
              }
            }
            if (commonOptions.quiet) { log = StringLogger() }
            val result = CommandPlugin
              .runCommandWithArgs(name, remaining, log, commonOptions)
            result match {
              case Right(_) =>
                if (commonOptions.quiet) { System.out.println(log.summary) }
                0
              case Left(messages) =>
                if (commonOptions.quiet) { highestSeverity(messages) + 1 }
                else { Messages.logMessages(messages, log) + 1 }
            }
          }
        case None =>
          // arguments are bad, error message will have been displayed
          log.info("Option parsing failed, terminating.")
          1
      }
    } catch {
      case NonFatal(exception) =>
        log.severe("Exception Thrown:", exception)
        SevereError.severity + 1
    }
  }

}

/** The service interface for Riddlc command plugins */
abstract class CommandPlugin[OPT <: CommandOptions: ClassTag](
  val pluginName: String)
    extends PluginInterface {
  final override def pluginVersion: String = RiddlBuildInfo.version

  val optionsClass: Class[?] = classTag[OPT].runtimeClass

  /** Provide an scopt OParser for the commands options type, OPT
    * @return
    *   A pair: the OParser and the default values for OPT
    */
  def getOptions: (OParser[Unit, OPT], OPT)

  def parseOptions(
    args: Array[String]
  ): Option[OPT] = {
    val (parser, default) = getOptions
    val (result, effects) = OParser.runParser(parser, args, default)
    OParser.runEffects(effects)
    result
  }

  /** Provide a typesafe/Config reader for the commands options. This reader
    * should read an object having the same name as the command. The fields of
    * that object must correspond to the fields of the OPT type.
    * @return
    *   A pureconfig.ConfigReader[OPT] that knows how to read OPT
    */
  def getConfigReader: ConfigReader[OPT]

  def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, OPT] = {
    if (commonOptions.verbose) {
      println(s"Reading command options from: $configFile")
    }
    ConfigSource.file(configFile).load[OPT](getConfigReader) match {
      case Right(value) =>
        if (commonOptions.verbose) {
          println(s"Read command options from $configFile")
        }
        if (commonOptions.debug) {
          import com.reactific.riddl.utils.StringHelpers.toPrettyString
          println(toPrettyString(value, 1))
        }
        Right(value)
      case Left(fails) => Left(
          errors("Errors while reading ${configFile}:\n" + fails.prettyPrint(1))
        )
    }
  }

  def runFrom(
    configFile: Path,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    loadOptionsFrom(configFile, commonOptions)
      .flatMap(run(_, commonOptions, log))
  }

  /** Execute the command given the options. Error should be returned as
    * Left(messages) and not directly logged. The log is for verbose or debug
    * output
    * @param options
    *   The command specific options
    * @param commonOptions
    *   The options common to all commands
    * @param log
    *   A logger for logging errors, warnings, and info
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  def run(
    @unused
    options: OPT,
    @unused
    commonOptions: CommonOptions,
    @unused
    log: Logger
  ): Either[Messages, Unit] = {
    Left(severes(
      s"""In command '$pluginName':
         |the CommandPlugin.run(OPT,CommonOptions,Logger) method was not overridden"""
        .stripMargin
    ))
  }

  def run(
    args: Array[String],
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    val maybeOptions: Option[OPT] = parseOptions(args)
    maybeOptions match {
      case Some(opts: OPT) =>
        val command = args.mkString(" ")
        if (commonOptions.verbose) { println(s"Running command: $command") }
        val result = Riddl.timer(command, show = commonOptions.showTimes, log) {
          run(opts, commonOptions, log)
        }
        result
      case Some(_) => Left(
          errors(s"Failed to match option type ${optionsClass.getSimpleName}")
        )
      case None => Left(errors(s"Failed to parse $pluginName options"))
    }
  }

  type OptionPlacer[V] = (V, OPT) => OPT
  protected val builder: OParserBuilder[OPT] = OParser.builder[OPT]
  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, OPT] = {
    arg[File]("input-file").required().action((v, c) => f(v, c))
      .text("required riddl input file to read")
  }

  def outputDir(f: OptionPlacer[File]): OParser[File, OPT] = {
    opt[File]('o', "output-dir").optional().action((v, c) => f(v, c))
      .text("required output directory for the generated output")
  }

  def replaceInputFile(
    options: OPT,
    @unused
    inputFile: Path
  ): OPT = options

  def resolveInputFileToConfigFile(
    options: OPT,
    commonOptions: CommonOptions,
    configFile: Path
  ): OPT = {
    options.inputFile match {
      case Some(inFile) =>
        val parent = configFile.getParent.toAbsolutePath
        val input = parent.resolve(inFile)
        val result = replaceInputFile(options, input)
        if (commonOptions.debug) {
          val pretty = toPrettyString(
            result,
            1,
            Some(s"Loaded these options:${System.lineSeparator()}")
          )
          println(pretty)
        }
        result
      case None => options
    }
  }
}
