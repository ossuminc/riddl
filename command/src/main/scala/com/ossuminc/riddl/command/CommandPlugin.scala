/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.SevereError
import com.ossuminc.riddl.language.Messages.errors
import com.ossuminc.riddl.language.Messages.highestSeverity
import com.ossuminc.riddl.language.Messages.severes
import com.ossuminc.riddl.utils.*
import com.ossuminc.riddl.passes.PassesResult
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
    if commonOptions.verbose then { println(s"Loading command: $name") }
    val loaded = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]](pluginsDir)
    if loaded.isEmpty then { Left(errors(s"No command found for '$name'")) }
    else {
      loaded.find(_.pluginName == name) match {
        case Some(pl) if pl.isInstanceOf[CommandPlugin[CommandOptions]] =>
          Right(pl)
        case Some(plugin) =>
          Left(
            errors(
              s"Plugin for command $name is the wrong type ${plugin.getClass.getSimpleName}"
            )
          )
        case None => Left(errors(s"No plugin command matches '$name'"))
      }
    }
  }

  private def runCommandWithArgs(
    name: String,
    args: Array[String],
    log: Logger,
    commonOptions: CommonOptions,
    pluginsDir: Path = Plugin.pluginsDir
  ): Either[Messages, PassesResult] = {
    val result = loadCommandNamed(name, commonOptions, pluginsDir)
      .flatMap { cmd => cmd.run(args, commonOptions, log) }
    if commonOptions.verbose then {
      val rc = if result.isRight then "yes" else "no"
      println(s"Ran: ${args.mkString(" ")}: success=$rc")
    }
    result
  }

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    pluginsDir: Path = Plugin.pluginsDir,
    outputDirOverride: Option[Path] = None
  ): Either[Messages, PassesResult] = {
    if commonOptions.verbose then {
      println(s"About to run $name with options from $optionsPath")
    }
    loadCommandNamed(name, commonOptions, pluginsDir).flatMap { cmd =>
      cmd.loadOptionsFrom(optionsPath, commonOptions).flatMap { opts =>
        cmd.run(opts, commonOptions, log, outputDirOverride) match {
          case Left(errors) =>
            if commonOptions.debug then {
              println(s"Errors after running '$name':")
              println(errors.format)
            }
            Left(errors)
          case Right(passesResult) => Right(passesResult)
        }
      }
    }
  }

  def loadCandidateCommands(
    configFile: Path,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, Seq[String]] = {
    val names = ConfigSource
      .file(configFile.toFile)
      .value()
      .map(_.keySet().asScala.toSeq)
    names match {
      case Right(value) =>
        if commonOptions.verbose then {
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
  ): Either[Messages, PassesResult] = {
    val result = CommandOptions.withInputFile[PassesResult](configFile, commandName) { path =>
      CommandPlugin
        .loadCandidateCommands(path, commonOptions)
        .flatMap { names =>
          if names.contains(targetCommand) then {
            CommandPlugin
              .runCommandNamed(targetCommand, path, log, commonOptions) match {
              case Left(errors) =>
                if commonOptions.debug then {
                  println(s"Errors after running `$targetCommand`:")
                  println(errors.format)
                }
                Left(errors)
              case result: Right[Messages, PassesResult] => result
            }
          } else {
            Left[Messages, PassesResult](
              errors(
                s"Command '$targetCommand' is not defined in $path"
              )
            )
          }
        }
    }
    handleCommandResult(result, commonOptions, log)
    result
  }

  private def handleCommandResult(
    result: Either[Messages, PassesResult],
    commonOptions: CommonOptions,
    log: Logger
  ): Int = {
    result match {
      case Right(passesResult: PassesResult) =>
        if passesResult.commonOptions.quiet then {
          System.out.println(log.summary)
        } else {
          Messages.logMessages(passesResult.messages, log, passesResult.commonOptions)
        }
        if passesResult.commonOptions.warningsAreFatal && passesResult.messages.hasWarnings then 1
        else 0
      case Left(messages) =>
        if commonOptions.quiet then { highestSeverity(messages) + 1 }
        else { Messages.logMessages(messages, log, commonOptions) + 1 }
    }
  }

  private def handleCommandRun(
    remaining: Array[String],
    commonOptions: CommonOptions
  ): Int = {
    val log: Logger =
      if commonOptions.quiet then StringLogger()
      else SysLogger()

    if remaining.isEmpty then
      log.error("No command argument was provided")
      1
    else
      val name = remaining.head
      if commonOptions.dryRun then
        log.info(s"Would have executed: ${remaining.mkString(" ")}")
        0
      else
        val result = CommandPlugin.runCommandWithArgs(name, remaining, log, commonOptions)
        handleCommandResult(result, commonOptions, log)
  }

  def runMainForTest(args: Array[String]): Either[Messages, PassesResult] = {
    try {
      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match
        case Some(commonOptions) =>
          val log: Logger = if commonOptions.quiet then StringLogger() else SysLogger()
          if remaining.isEmpty then Left(List(Messages.error("No command argument was provided")))
          else
            val name = remaining.head
            CommandPlugin.runCommandWithArgs(name, remaining, log, commonOptions)
        case None =>
          Left(List(Messages.error("Option parsing failed, terminating.")))
    } catch {
      case NonFatal(exception) =>
        Left(List(Messages.severe("Exception Thrown:", exception, At.empty)))
    }
  }

  def runMain(args: Array[String], log: Logger = SysLogger()): Int = {
    try {
      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(commonOptions) =>
          handleCommandRun(remaining, commonOptions)
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
trait CommandPlugin[OPT <: CommandOptions: ClassTag](val pluginName: String) extends PluginInterface {
  final override def pluginVersion: String = RiddlBuildInfo.version

  private val optionsClass: Class[?] = classTag[OPT].runtimeClass

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

  /** Provide a typesafe/Config reader for the commands options. This reader should read an object having the same name
    * as the command. The fields of that object must correspond to the fields of the OPT type.
    * @return
    *   A pureconfig.ConfigReader[OPT] that knows how to read OPT
    */
  def getConfigReader: ConfigReader[OPT]

  def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, OPT] = {
    if commonOptions.verbose then {
      println(s"Reading command options from: $configFile")
    }
    ConfigSource.file(configFile).load[OPT](getConfigReader) match {
      case Right(value) =>
        if commonOptions.verbose then {
          println(s"Read command options from $configFile")
        }
        if commonOptions.debug then {
          println(StringHelpers.toPrettyString(value, 1))
        }
        Right(value)
      case Left(fails) =>
        Left(
          errors(
            s"Errors while reading $configFile:\n" + fails.prettyPrint(1)
          )
        )
    }
  }

  /** Execute the command given the options. Error should be returned as Left(messages) and not directly logged. The log
    * is for verbose or debug output
    *
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
    @unused options: OPT,
    @unused commonOptions: CommonOptions,
    @unused log: Logger,
    @unused outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    Left(
      severes(
        s"""In command '$pluginName':
         |the CommandPlugin.run(OPT,CommonOptions,Logger) method was not overridden""".stripMargin
      )
    )
  }

  def run(
    args: Array[String],
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path] = None
  ): Either[Messages, PassesResult] = {
    val maybeOptions: Option[OPT] = parseOptions(args)
    maybeOptions match {
      case Some(opts: OPT) =>
        val command = args.mkString(" ")
        if commonOptions.verbose then { println(s"Running command: $command") }
        val result = Timer.time(command, show = commonOptions.showTimes, log) {
          run(opts, commonOptions, log, outputDirOverride)
        }
        result
      case None => Left(errors(s"Failed to parse $pluginName options"))
    }
  }

  private type OptionPlacer[V] = (V, OPT) => OPT
  protected val builder: OParserBuilder[OPT] = OParser.builder[OPT]
  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, OPT] = {
    arg[File]("input-file")
      .required()
      .action((v, c) => f(v, c))
      .text("required riddl input file to read")
  }

  def outputDir(f: OptionPlacer[File]): OParser[File, OPT] = {
    opt[File]('o', "output-dir")
      .optional()
      .action((v, c) => f(v, c))
      .text("required output directory for the generated output")
  }

  def replaceInputFile(
    options: OPT,
    @unused inputFile: Path
  ): OPT = options

  def resolveInputFileToConfigFile(
    options: OPT,
    commonOptions: CommonOptions,
    configFile: Path
  ): OPT = {
    options.inputFile match {
      case Some(inFile) =>
        val parent = Option(configFile.getParent) match {
          case Some(path) => path
          case None       => Path.of(".")
        }
        val input = parent.resolve(inFile)
        val result = replaceInputFile(options, input)
        if commonOptions.debug then {
          val pretty = StringHelpers.toPrettyString(
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
