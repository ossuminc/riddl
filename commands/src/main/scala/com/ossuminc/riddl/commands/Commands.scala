package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{Logger, StringLogger, SysLogger}
import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import pureconfig.error.ConfigReaderFailures

import java.nio.file.Path
import pureconfig.{ConfigCursor, ConfigObjectCursor, ConfigReader, ConfigSource}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.CollectionHasAsScala

object Commands:
  def loadCommandNamed(
    name: String,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, Command[?]] =
    if commonOptions.verbose then
      println(s"Loading command: $name")
    end if
    name match
      case "about" => Right(AboutCommand())
      case "dump" => Right(DumpCommand())
      case "flatten" => Right(FlattenCommand())
      case "from" => Right(FromCommand())
      case "help" => Right(HelpCommand())
      case "hugo" => Right(HugoCommand())
      case "info" => Right(InfoCommand())
      case "onchange" => Right(OnChangeCommand())
      case "parse" => Right(ParseCommand())
      case "prettify" => Right(PrettifyCommand())
      case "repeat" => Right(RepeatCommand())
      case "stats" => Right(StatsCommand())
      case "validate" => Right(ValidateCommand())
      case "version" => Right(VersionCommand())
      case _ => Left(errors(s"No command found for '$name'"))
    end match
  end loadCommandNamed

  private def runCommandWithArgs(
    name: String,
    args: Array[String],
    log: Logger,
    commonOptions: CommonOptions
  ): Either[Messages, PassesResult] =
    val result = loadCommandNamed(name, commonOptions)
      .flatMap { cmd => cmd.run(args, commonOptions, log) }
    if commonOptions.verbose then
      val rc = if result.isRight then "yes" else "no"
      println(s"Ran: ${args.mkString(" ")}: success=$rc")
    end if
    result
  end runCommandWithArgs

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions(),
    outputDirOverride: Option[Path] = None
  ): Either[Messages, PassesResult] =
    if commonOptions.verbose then
      println(s"About to run $name with options from $optionsPath")
    end if
    loadCommandNamed(name, commonOptions).flatMap { cmd =>
      cmd.loadOptionsFrom(optionsPath, commonOptions).flatMap { opts =>
        cmd.run(opts, commonOptions, log, outputDirOverride) match
          case Left(errors) =>
            if commonOptions.debug then {
              println(s"Errors after running '$name':")
              println(errors.format)
            }
            Left(errors)
          case Right(passesResult) => Right(passesResult)
        end match
      }
    }
  end runCommandNamed

  def loadCandidateCommands(
    configFile: Path,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, Seq[String]] =
    val names = ConfigSource
      .file(configFile.toFile)
      .value()
      .map(_.keySet().asScala.toSeq)
    names match
      case Right(value) =>
        if commonOptions.verbose then
          println(s"Found candidate commands in $configFile: ${value.mkString(" ")}")
        Right(value)
      case Left(fails) =>
        val message = s"Errors while reading $configFile:\n" + fails.prettyPrint(1)
        Left(errors(message))
    end match
  end loadCandidateCommands

  def runFromConfig(
    configFile: Option[Path],
    targetCommand: String,
    commonOptions: CommonOptions,
    log: Logger,
    commandName: String
  ): Either[Messages, PassesResult] =
    val result = CommandOptions.withInputFile[PassesResult](configFile, commandName) { path =>
      loadCandidateCommands(path, commonOptions).flatMap { names =>
        if names.contains(targetCommand) then
          runCommandNamed(targetCommand, path, log, commonOptions) match
            case Left(errors) =>
              if commonOptions.debug then
                println(s"Errors after running `$targetCommand`:")
                println(errors.format)
              Left(errors)
            case result: Right[Messages, PassesResult] => result
          end match
        else
          Left[Messages, PassesResult](errors(s"Command '$targetCommand' is not defined in $path"))
        end if
      }
    }
    handleCommandResult(result, commonOptions, log)
    result
  end runFromConfig

  private def handleCommandResult(
    result: Either[Messages, PassesResult],
    commonOptions: CommonOptions,
    log: Logger
  ): Int =
    result match
      case Right(passesResult: PassesResult) =>
        if passesResult.commonOptions.quiet then
          System.out.println(log.summary)
        else
          logMessages(passesResult.messages, log, passesResult.commonOptions)
        if passesResult.commonOptions.warningsAreFatal && passesResult.messages.hasWarnings then 1 else 0
      case Left(messages) =>
        if commonOptions.quiet then highestSeverity(messages) + 1
        else {
          logMessages(messages, log, commonOptions) + 1
        }
    end match
  end handleCommandResult

  private def handleCommandRun(
    remaining: Array[String],
    commonOptions: CommonOptions,
    log: Logger
  ): Int =
    if remaining.isEmpty then
      log.error("No command argument was provided")
      1
    else
      val name = remaining.head
      if commonOptions.dryRun then
        log.info(s"Would have executed: ${remaining.mkString(" ")}")
        0
      else
        val result = runCommandWithArgs(name, remaining, log, commonOptions)
        handleCommandResult(result, commonOptions, log)
  end handleCommandRun

  def runMainForTest(args: Array[String]): Either[Messages, PassesResult] =
    try
      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match
        case Some(commonOptions) =>
          val log: Logger = if commonOptions.quiet then StringLogger() else SysLogger()
          if remaining.isEmpty then
            Left(List(error("No command argument was provided")))
          else
            val name = remaining.head
            runCommandWithArgs(name, remaining, log, commonOptions)
        case None =>
          Left(List(error("Option parsing failed, terminating.")))
      end match
    catch
      case NonFatal(exception) =>
        Left(List(severe("Exception Thrown:", exception, At.empty)))
  end runMainForTest

  def runMain(args: Array[String], log: Logger = SysLogger()): Int =
    try
      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match
        case Some(commonOptions) =>
          handleCommandRun(remaining, commonOptions, log)
        case None =>
          // arguments are bad, error message will have been displayed
          log.info("Option parsing failed, terminating.")
          1
      end match
    catch
      case NonFatal(exception) =>
        log.severe("Exception Thrown:", exception)
        SevereError.severity + 1
  end runMain

  def parseCommandOptions(
   args: Array[String]
  ): Either[Messages, CommandOptions] =
    require(args.nonEmpty)
    val result = loadCommandNamed(args.head)
    result match
      case Right(cmd) =>
        cmd.parseOptions(args) match {
          case Some(options) => Right(options)
          case None => Left(errors("RiddlOption parsing failed"))
        }
      case Left(messages) => Left(messages)
    end match
  end parseCommandOptions

  /** A helper function for reading optional items from a config file.
   *
   * @param objCur
   * The ConfigObjectCursor to start with
   * @param key
   * The name of the optional config item
   * @param default
   * The default value of the config item
   * @param mapIt
   * The function to map ConfigCursor to ConfigReader.Result[T]
   * @tparam T
   * The Scala type of the config item's value
   * @return
   * The reader for this optional configuration item.
   */
  def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T
  )(mapIt: ConfigCursor => ConfigReader.Result[T]): ConfigReader.Result[T] =
    objCur.atKeyOrUndefined(key) match
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur => mapIt(stCur)
    end match
  end optional
end Commands

