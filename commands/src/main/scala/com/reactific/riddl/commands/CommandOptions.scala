package com.reactific.riddl.commands
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.Messages.{Messages, errors}
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigCursor, ConfigObjectCursor, ConfigReader, ConfigSource}

import java.nio.file.Path

/**
 * Base class for command options. Every command should extend this to a case
 * class
 */
trait CommandOptions {
  def command: String
  def inputFile: Option[Path]

  def withInputFile(f: Path => Either[Messages,Unit]): Either[Messages, Unit] = {
    CommandOptions.withInputFile(inputFile, command)(f)
  }
}

object CommandOptions {
  def withInputFile(inputFile: Option[Path], commandName: String)(
    f: Path => Either[Messages, Unit]
  ): Either[Messages, Unit] = {
    inputFile match {
      case Some(inputFile) =>
        f(inputFile)
      case None =>
        Left(List(
          Messages.error(s"No input file specified for $commandName")
        ))
    }
  }

  val empty: CommandOptions = new CommandOptions {
    def command: String = "unspecified"
    def inputFile: Option[Path] = Option.empty[Path]
  }

  /** A helper function for reading optional items from a config file.
   *
   * @param objCur  The ConfigObjectCursor to start with
   * @param key     The name of the optional config item
   * @param default The default value of the config item
   * @param mapIt   The function to map ConfigCursor to ConfigReader.Result[T]
   * @tparam T The Scala type of the config item's value
   * @return The reader for this optional configuration item.
   */
  def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T
  )(
    mapIt: ConfigCursor => ConfigReader.Result[T]
  ): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur => mapIt(stCur)
    }
  }

  private def noBool = Option.empty[Boolean]

  implicit val commonOptionsReader: ConfigReader[CommonOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showTimes <- optional(objCur, "show-times", noBool)(c =>
          c.asBoolean.map(Option(_))
        )
        verbose <- optional(objCur, "verbose", noBool)(cc =>
          cc.asBoolean.map(Option(_))
        )
        dryRun <- optional(objCur, "dry-run", noBool)(cc =>
          cc.asBoolean.map(Option(_))
        )
        quiet <- optional(objCur, "quiet", noBool)(cc =>
          cc.asBoolean.map(Option(_))
        )
        debug <- optional(objCur, "debug", noBool)(cc =>
          cc.asBoolean.map(Option(_))
        )
        suppressWarnings <- optional(objCur, "suppress-warnings", noBool) {
          cc => cc.asBoolean.map(Option(_))
        }
        suppressStyleWarnings <-
          optional(objCur, "suppress-style-warnings", noBool) { cc =>
            cc.asBoolean.map(Option(_))
          }
        suppressMissingWarnings <-
          optional(objCur, "suppress-missing-warnings", noBool) { cc =>
            cc.asBoolean.map(Option(_))
          }
        pluginsDir <- optional(objCur, "plugins-dir", Option.empty[Path]) {
          cc => cc.asString.map(f => Option(Path.of(f)))
        }
        common <- optional[CommonOptions](objCur, "common", CommonOptions()) {
          cur => commonOptionsReader.from(cur)
        }
      } yield {
        CommonOptions(
          showTimes.getOrElse(common.showTimes),
          verbose.getOrElse(common.verbose),
          dryRun.getOrElse(common.dryRun),
          quiet.getOrElse(common.quiet),
          showWarnings = suppressWarnings.map(!_)
            .getOrElse(common.showWarnings),
          showMissingWarnings = suppressMissingWarnings.map(!_)
            .getOrElse(common.showMissingWarnings),
          showStyleWarnings = suppressStyleWarnings.map(!_)
            .getOrElse(common.showStyleWarnings),
          debug.getOrElse(common.debug),
          pluginsDir
        )
      }
    }
  }

  final def loadCommonOptions(
    path: Path,
  ): Either[Messages,CommonOptions] = {
    ConfigSource.file(path.toFile).load[CommonOptions] match {
      case Right(options) =>
        if (options.verbose) {
          import com.reactific.riddl.utils.StringHelpers.toPrettyString
          println(toPrettyString(options,1,Some("Loaded common options:")))
        }
        Right(options)
      case Left(failures) =>
        Left(errors(s"Failed to load options from $path because:\n" +
          failures.prettyPrint(1)))
    }
  }
}
