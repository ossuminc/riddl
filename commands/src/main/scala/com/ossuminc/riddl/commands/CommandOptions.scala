/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.errors
import com.ossuminc.riddl.utils.StringHelpers.*
import com.ossuminc.riddl.utils.Plugin
import pureconfig.error.ConfigReaderFailures
import pureconfig.ConfigCursor
import pureconfig.ConfigObjectCursor
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import scopt.OParser
import scala.concurrent.duration.{FiniteDuration, DurationInt}

import java.nio.file.Path

/** Base class for command options. Every command should extend this to a case class
  */
trait CommandOptions {
  def command: String

  def inputFile: Option[Path]

  def withInputFile[S](
    f: Path => Either[Messages, S]
  ): Either[Messages, S] = {
    CommandOptions.withInputFile(inputFile, command)(f)
  }
}

object CommandOptions {

  def withInputFile[S](
    inputFile: Option[Path],
    commandName: String
  )(
    f: Path => Either[Messages, S]
  ): Either[Messages, S] = {
    inputFile match {
      case Some(inputFile) => f(inputFile)
      case None =>
        Left(List(Messages.error(s"No input file specified for $commandName")))
    }
  }

  val empty: CommandOptions = new CommandOptions {
    def command: String = "unspecified"
    def inputFile: Option[Path] = Option.empty[Path]
  }

  /** A helper function for reading optional items from a config file.
    *
    * @param objCur
    *   The ConfigObjectCursor to start with
    * @param key
    *   The name of the optional config item
    * @param default
    *   The default value of the config item
    * @param mapIt
    *   The function to map ConfigCursor to ConfigReader.Result[T]
    * @tparam T
    *   The Scala type of the config item's value
    * @return
    *   The reader for this optional configuration item.
    */
  def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T
  )(mapIt: ConfigCursor => ConfigReader.Result[T]): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur                      => mapIt(stCur)
    }
  }

  private def noBool = Option.empty[Boolean]

  implicit val commonOptionsReader: ConfigReader[CommonOptions] = { (cur: ConfigCursor) =>
    {
      val default = CommonOptions()
      for
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey("common")
        objCur <- topRes.asObjectCursor
        showTimes <- optional(objCur, "show-times", default.showTimes)(c => c.asBoolean)
        showIncludeTimes <- optional(objCur, "show-include-times", default.showIncludeTimes)(c => c.asBoolean)
        verbose <- optional(objCur, "verbose", default.verbose)(cc => cc.asBoolean)
        dryRun <- optional(objCur, "dry-run", default.dryRun)(cc => cc.asBoolean)
        quiet <- optional(objCur, "quiet", default.quiet)(cc => cc.asBoolean)
        debug <- optional(objCur, "debug", default.debug)(cc => cc.asBoolean)
        noANSIMessages <- optional(objCur, "no-ansi-messages", default.noANSIMessages)(cc => cc.asBoolean)
        sortMessages <- optional(objCur, "sort-messages-by-location", default.sortMessagesByLocation)(cc =>
          cc.asBoolean
        )
        groupMessagesByKind <- optional(objCur, "group-messages-by-kind", default.groupMessagesByKind)(cc =>
          cc.asBoolean
        )
        suppressWarnings <- optional(objCur, "suppress-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressStyleWarnings <- optional(objCur, "suppress-style-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressMissingWarnings <- optional(objCur, "suppress-missing-warnings", noBool)(cc =>
          cc.asBoolean.map(Option(_))
        )
        suppressUsageWarnings <- optional(objCur, "suppress-usage-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressInfoMessages <- optional(objCur, "suppress-info-messages", noBool)(cc => cc.asBoolean.map(Option(_)))
        hideWarnings <- optional(objCur, "hide-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        hideStyleWarnings <- optional(objCur, "hide-style-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        hideMissingWarnings <- optional(objCur, "hide-missing-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        hideUsageWarnings <- optional(objCur, "hide-usage-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        hideInfoMessages <- optional(objCur, "hide-info-messages", noBool)(cc => cc.asBoolean.map(Option(_)))
        showWarnings <- optional[Boolean](objCur, "show-warnings", default.showWarnings)(cc => cc.asBoolean)
        showStyleWarnings <- optional[Boolean](objCur, "show-style-warnings", default.showStyleWarnings)(cc => 
          cc.asBoolean)
        showMissingWarnings <- optional[Boolean](objCur, "show-missing-warnings", default.showMissingWarnings)(cc =>
          cc.asBoolean)
        showUsageWarnings <- optional[Boolean](objCur, "show-usage-warnings", default.showUsageWarnings)(cc =>
          cc.asBoolean)
        showInfoMessages <- optional[Boolean](objCur, "show-info-messages", default.showInfoMessages)(cc =>
          cc.asBoolean)
        pluginsDir <- optional(objCur, "plugins-dir", Option.empty[Path])(cc =>
          cc.asString.map(f => Option(Path.of(f)))
        )
        maxParallel <- optional[Int](objCur, "max-parallel-parsing", default.maxParallelParsing)(cc => cc.asInt)
        maxIncludeWait <- optional[Long](objCur, "max-include-wait", default.maxIncludeWait.toSeconds)(cc => cc.asLong)
        warnsAreFatal <- optional(objCur, "warnings-are-fatal", default.warningsAreFatal)(cc => cc.asBoolean)
      yield {
        val shouldShowWarnings = suppressWarnings
          .map(!_)
          .getOrElse(hideWarnings.map(!_).getOrElse(showWarnings))
        val shouldShowMissing = suppressMissingWarnings
          .map(!_)
          .getOrElse(hideMissingWarnings.map(!_).getOrElse(showMissingWarnings))
        val shouldShowStyle = suppressStyleWarnings
          .map(!_)
          .getOrElse(hideStyleWarnings.map(!_).getOrElse(showStyleWarnings))
        val shouldShowUsage = suppressUsageWarnings
          .map(!_)
          .getOrElse(hideUsageWarnings.map(!_).getOrElse(showUsageWarnings))
        val shouldShowInfos = suppressInfoMessages
          .map(!_)
          .getOrElse(hideInfoMessages.map(!_).getOrElse(showInfoMessages))
        CommonOptions(
          showTimes,
          showIncludeTimes,
          verbose,
          dryRun,
          quiet,
          shouldShowWarnings,
          shouldShowMissing,
          shouldShowStyle,
          shouldShowUsage,
          shouldShowInfos,
          debug,
          pluginsDir,
          sortMessagesByLocation = sortMessages,
          groupMessagesByKind = groupMessagesByKind,
          noANSIMessages = noANSIMessages,
          maxParallelParsing = maxParallel,
          maxIncludeWait = FiniteDuration(maxIncludeWait, "seconds"),
          warningsAreFatal = warnsAreFatal
        )
      }
    }
  }

  final def loadCommonOptions(
    path: Path
  ): Either[Messages, CommonOptions] = {
    ConfigSource.file(path.toFile).load[CommonOptions] match {
      case Right(options) =>
        if options.debug then {
          println(toPrettyString(options, 1, Some("Loaded common options:")))
        }
        Right(options)
      case Left(failures) =>
        Left(
          errors(
            s"Failed to load options from $path because:\n" +
              failures.prettyPrint(1)
          )
        )
    }
  }

  def parseCommandOptions(
    args: Array[String]
  ): Either[Messages, CommandOptions] = {
    require(args.nonEmpty)
    val result = CommandPlugin.loadCommandNamed(args.head)
    result match {
      case Right(cmd) =>
        cmd.parseOptions(args) match {
          case Some(options) => Right(options)
          case None          => Left(errors("RiddlOption parsing failed"))
        }
      case Left(messages) => Left(messages)
    }
  }

  val commandOptionsParser: OParser[Unit, CommandOptions] = {
    val plugins = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]]()
    val list =
      for plugin <- plugins yield { plugin.pluginName -> plugin.getOptions }
    val parsers = list.sortBy(_._1).map(_._2._1) // alphabetize
    require(parsers.nonEmpty, "No command line options parsers!")
    OParser.sequence(parsers.head, parsers.drop(1)*)
  }

}
