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
import com.ossuminc.riddl.utils.Plugin
import pureconfig.error.ConfigReaderFailures
import pureconfig.ConfigCursor
import pureconfig.ConfigObjectCursor
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import scopt.OParser

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
      for
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey("common")
        objCur <- topRes.asObjectCursor
        showTimes <-
          optional[Boolean](objCur, "show-times", false)(c => c.asBoolean)
        verbose <- optional(objCur, "verbose", false)(cc => cc.asBoolean)
        dryRun <- optional(objCur, "dry-run", false)(cc => cc.asBoolean)
        quiet <- optional(objCur, "quiet", false)(cc => cc.asBoolean)
        debug <- optional(objCur, "debug", false)(cc => cc.asBoolean)
        noANSIMessages <- optional(objCur, "noANSIMessages", false)(cc => cc.asBoolean)
        sortMessages <- optional(objCur, "sort-messages-by-location", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressWarnings <- optional(objCur, "suppress-warnings", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressStyleWarnings <- optional(objCur, "suppress-style-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        suppressMissingWarnings <- optional(objCur, "suppress-missing-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        suppressUsageWarnings <- optional(objCur, "suppress-usage-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        suppressInfoMessages <- optional(objCur, "suppress-info-messages", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        hideWarnings <- optional(objCur, "hide-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        hideStyleWarnings <- optional(objCur, "hide-style-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        hideMissingWarnings <- optional(objCur, "hide-missing-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        hideUsageWarnings <- optional(objCur, "hide-usage-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        hideInfoMessages <- optional(objCur, "hide-info-messages", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        showWarnings <- optional(objCur, "show-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        showStyleWarnings <- optional(objCur, "show-style-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        showMissingWarnings <- optional(objCur, "show-missing-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        showUsageWarnings <- optional(objCur, "show-usage-warnings", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        showInfoMessages <- optional(objCur, "show-info-messages", noBool) { cc =>
          cc.asBoolean.map(Option(_))
        }
        pluginsDir <- optional(objCur, "plugins-dir", Option.empty[Path]) { cc =>
          cc.asString.map(f => Option(Path.of(f)))
        }
        maxParallel <- optional(objCur, "max-parallel-parsing", Some(4)) { cc =>
          cc.asInt.map(Option(_))
        }
        warnsAreFatal <- optional(objCur, "warnings-are-fatal", Option.empty[Boolean]) { cc =>
          cc.asBoolean.map(Option(_))
        }
      yield {
        val default = CommonOptions()
        val shouldShowWarnings = suppressWarnings
          .map(!_)
          .getOrElse(
            hideWarnings
              .map(!_)
              .getOrElse(
                showWarnings.getOrElse(
                  default.showWarnings
                )
              )
          )
        val shouldShowMissing = suppressMissingWarnings
          .map(!_)
          .getOrElse(
            hideMissingWarnings
              .map(!_)
              .getOrElse(
                showMissingWarnings.getOrElse(default.showMissingWarnings)
              )
          )
        val shouldShowStyle = suppressStyleWarnings
          .map(!_)
          .getOrElse(
            hideStyleWarnings
              .map(!_)
              .getOrElse(showStyleWarnings.getOrElse(default.showStyleWarnings))
          )
        val shouldShowUsage = suppressUsageWarnings
          .map(!_)
          .getOrElse(hideUsageWarnings.map(!_).getOrElse(showUsageWarnings.getOrElse(default.showUsageWarnings)))
        val shouldShowInfos = suppressInfoMessages
          .map(!_)
          .getOrElse(
            hideInfoMessages
              .map(!_)
              .getOrElse(showInfoMessages.getOrElse(default.showInfoMessages))
          )
        CommonOptions(
          showTimes,
          verbose,
          dryRun,
          quiet,
          showWarnings = shouldShowWarnings,
          showMissingWarnings = shouldShowMissing,
          showStyleWarnings = shouldShowStyle,
          showUsageWarnings = shouldShowUsage,
          showInfoMessages = shouldShowInfos,
          debug,
          pluginsDir,
          sortMessagesByLocation = sortMessages.getOrElse(false),
          maxParallelParsing = maxParallel.getOrElse(4),
          warningsAreFatal = warnsAreFatal.getOrElse(false)
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
          import com.ossuminc.riddl.utils.StringHelpers.toPrettyString
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

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  val commandOptionsParser: OParser[Unit, CommandOptions] = {
    val plugins = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]]()
    val list =
      for plugin <- plugins yield { plugin.pluginName -> plugin.getOptions }
    val parsers = list.sortBy(_._1).map(_._2._1) // alphabetize
    require(parsers.nonEmpty, "No command line options parsers!")
    OParser.sequence(parsers.head, parsers.drop(1)*)
  }

}
