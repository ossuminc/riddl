package com.reactific.riddl.commands
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Messages.errors
import com.reactific.riddl.utils.Plugin
import pureconfig.error.ConfigReaderFailures
import pureconfig.ConfigCursor
import pureconfig.ConfigObjectCursor
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import scopt.OParser

import java.nio.file.Path

/** Base class for command options. Every command should extend this to a case
  * class
  */
trait CommandOptions {
  def command: String
  def inputFile: Option[Path]

  def withInputFile(
    f: Path => Either[Messages, Unit]
  ): Either[Messages, Unit] = {
    CommandOptions.withInputFile(inputFile, command)(f)
  }
}

object CommandOptions {
  def withInputFile(
    inputFile: Option[Path],
    commandName: String
  )(f: Path => Either[Messages, Unit]
  ): Either[Messages, Unit] = {
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
  )(mapIt: ConfigCursor => ConfigReader.Result[T]
  ): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur                      => mapIt(stCur)
    }
  }

  private def noBool = Option.empty[Boolean]

  implicit val commonOptionsReader: ConfigReader[CommonOptions] = {
    (cur: ConfigCursor) =>
      {
        for {
          topCur <- cur.asObjectCursor
          topRes <- topCur.atKey("common")
          objCur <- topRes.asObjectCursor
          showTimes <-
            optional[Boolean](objCur, "show-times", false)(c => c.asBoolean)
          verbose <- optional(objCur, "verbose", false)(cc => cc.asBoolean)
          dryRun <- optional(objCur, "dry-run", false)(cc => cc.asBoolean)
          quiet <- optional(objCur, "quiet", false)(cc => cc.asBoolean)
          debug <- optional(objCur, "debug", false)(cc => cc.asBoolean)
          suppressWarnings <- optional(objCur, "suppress-warnings", noBool)(
            cc => cc.asBoolean.map(Option(_))
          )
          suppressStyleWarnings <-
            optional(objCur, "suppress-style-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          suppressMissingWarnings <-
            optional(objCur, "suppress-missing-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          hideWarnings <- optional(objCur, "hide-warnings", noBool)(cc =>
            cc.asBoolean.map(Option(_))
          )
          hideStyleWarnings <- optional(objCur, "hide-style-warnings", noBool) {
            cc => cc.asBoolean.map(Option(_))
          }
          hideMissingWarnings <-
            optional(objCur, "hide-missing-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          showWarnings <- optional(objCur, "show-warnings", noBool) { cc =>
            cc.asBoolean.map(Option(_))
          }
          showStyleWarnings <- optional(objCur, "show-style-warnings", noBool) {
            cc => cc.asBoolean.map(Option(_))
          }
          showMissingWarnings <-
            optional(objCur, "show-missing-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          showUnusedWarnings <-
            optional(objCur, "show-unused-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          pluginsDir <- optional(objCur, "plugins-dir", Option.empty[Path]) {
            cc => cc.asString.map(f => Option(Path.of(f)))
          }
        } yield {
          val default = CommonOptions()
          val shouldShowWarnings = suppressWarnings.map(!_)
            .getOrElse(hideWarnings.map(!_).getOrElse(showWarnings.getOrElse(
              default.showWarnings
            )))
          val shouldShowMissing = suppressMissingWarnings.map(!_)
            .getOrElse(hideMissingWarnings.map(!_).getOrElse(
              showMissingWarnings.getOrElse(default.showMissingWarnings)
            ))
          val shouldShowStyle = suppressStyleWarnings.map(!_).getOrElse(
            hideStyleWarnings.map(!_)
              .getOrElse(showStyleWarnings.getOrElse(default.showStyleWarnings))
          )
          CommonOptions(
            showTimes,
            verbose,
            dryRun,
            quiet,
            shouldShowWarnings,
            shouldShowMissing,
            shouldShowStyle,
            showUnusedWarnings = showUnusedWarnings
              .getOrElse(default.showStyleWarnings),
            debug,
            pluginsDir
          )
        }
      }
  }

  final def loadCommonOptions(
    path: Path
  ): Either[Messages, CommonOptions] = {
    ConfigSource.file(path.toFile).load[CommonOptions] match {
      case Right(options) =>
        if (options.debug) {
          import com.reactific.riddl.utils.StringHelpers.toPrettyString
          println(toPrettyString(options, 1, Some("Loaded common options:")))
        }
        Right(options)
      case Left(failures) => Left(errors(
          s"Failed to load options from $path because:\n" +
            failures.prettyPrint(1)
        ))
    }
  }

  def parseCommandOptions(
    args: Array[String]
  ): Either[Messages, CommandOptions] = {
    require(args.nonEmpty)
    val result = CommandPlugin.loadCommandNamed(args.head)
    result match {
      case Right(cmd) => cmd.parseOptions(args) match {
          case Some(options) => Right(options)
          case None          => Left(errors("Option parsing failed"))
        }
      case Left(messages) => Left(messages)
    }
  }

  val commandOptionsParser: OParser[Unit, CommandOptions] = {
    val plugins = Plugin.loadPluginsFrom[CommandPlugin[CommandOptions]]()
    val list =
      for { plugin <- plugins } yield { plugin.pluginName -> plugin.getOptions }
    val parsers = list.sortBy(_._1).map(_._2._1) // alphabetize
    OParser.sequence(parsers.head, parsers.tail*)
  }

}
