/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommandOptions.optional
import com.ossuminc.riddl.command.{CommandOptions,CommandPlugin}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.errors
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.Logger

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.io.File
import java.nio.file.Path
import pureconfig.error.CannotParse

import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object OnChangeCommand {
  final val cmdName: String = "onchange"
  final val defaultMaxLoops = 1024
  case class Options(
    configFile: Path = Path.of("."),
    watchDirectory: Path = Path.of("."),
    targetCommand: String = ParseCommand.cmdName,
    refreshRate: FiniteDuration = 10.seconds,
    maxCycles: Int = defaultMaxLoops,
    interactive: Boolean = false)
      extends CommandOptions {
    def command: String = cmdName
    def inputFile: Option[Path] = None
  }
}

class OnChangeCommand
    extends CommandPlugin[OnChangeCommand.Options](OnChangeCommand.cmdName) {
  import OnChangeCommand.Options

  override def getOptions: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("onchange").children(
        arg[File]("config-file").required()
          .action((f, opts) => opts.copy(configFile = f.toPath))
          .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
        arg[File]("watch-directory").required()
          .action((f, opts) => opts.copy(watchDirectory = f.toPath))
          .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
        arg[String]("target-command").required().action { (cmd, opt) =>
          opt.copy(targetCommand = cmd)
        }.text("The name of the command to select from the configuration file"),
        arg[FiniteDuration]("refresh-rate").optional().validate {
          case r if r.toMillis < 1000 =>
            Left("<refresh-rate> is too fast, minimum is 1 seconds")
          case r if r.toDays > 1 =>
            Left("<refresh-rate> is too slow, maximum is 1 day")
          case _ => Right(())
        }.action((r, c) => c.copy(refreshRate = r))
          .text("""Specifies the rate at which the <git-clone-dir> is checked
                  |for updates so the process to regenerate the hugo site is
                  |started""".stripMargin),
        arg[Int]("max-cycles").optional().validate {
          case x if x < 1           => Left("<max-cycles> can't be less than 1")
          case x if x > 1024 * 1024 => Left("<max-cycles> is too big")
          case _                    => Right(())
        }.action((m, c) => c.copy(maxCycles = m))
          .text("""Limit the number of check cycles that will be repeated.""")
      ).text(
        """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the configured command is run""".stripMargin
      )
    ) -> Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    {
      for
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(OnChangeCommand.cmdName)
        objCur <- topRes.asObjectCursor
        configFileRes <- objCur.atKey("config-file")
        configFile <- configFileRes.asString.flatMap { inputPath =>
          if inputPath.isEmpty then {
            ConfigReader.Result.fail[String](
              CannotParse("'config-filew' requires a non-empty value", None)
            )
          } else Right(inputPath)
        }
        watchDirRes <- objCur.atKey("watch-directory")
        watchDir <- watchDirRes.asString.flatMap { watchDir =>
          if watchDir.isEmpty then {
            ConfigReader.Result.fail[String](
              CannotParse("'watch-directory' requires a non-empty value", None)
            )
          } else Right(watchDir)
        }
        targetCommandRes <- objCur.atKey("target-command")
        targetCmd <- targetCommandRes.asString.flatMap { targetCmd =>
          if targetCmd.isEmpty then {
            ConfigReader.Result.fail[String](
              CannotParse("'target-command' requires a non-empty value", None)
            )
          } else Right(targetCmd)
        }
        refreshRate <- optional(objCur, "refresh-rate", "10s")(_.asString).flatMap { rr =>
            val dur = Duration.create(rr)
            if dur.isFinite then { Right(dur.asInstanceOf[FiniteDuration]) }
            else {
              ConfigReader.Result.fail[FiniteDuration](CannotParse(
                s"'refresh-rate' must be a finite duration, not $rr",
                None
              ))
            }
          }
        maxCycles <- optional(objCur, "max-cycles", OnChangeCommand.defaultMaxLoops)(_.asInt)
        interactive <- optional(objCur, "interactive", true)(_.asBoolean)
      yield {
        OnChangeCommand.Options(
          configFile = Path.of(configFile),
          watchDirectory = Path.of(watchDir),
          targetCommand = targetCmd,
          refreshRate = refreshRate,
          maxCycles = maxCycles,
          interactive = interactive
        )
      }
    }
  }

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(configFile = inputFile) }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }

  /** Execute the command given the options. Error should be returned as
    * Left(messages) and not directly logged. The log is for verbose or debug
    * output
   *
   * @param options
    *   The command specific options
    * @param commonOptions
    *   The options common to all commands
   * @param log
   *    A logger for logging errors, warnings, and info
   * @return
   * Either a set of Messages on error or a Unit on success
   */
  override def run(
                    options: Options,
                    commonOptions: CommonOptions,
                    log: Logger,
                    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {Left(errors("Not Implemented"))}

  private final val timeStampFileName: String = ".riddl-timestamp"
  def getTimeStamp(dir: Path): FileTime = {
    val filePath = dir.resolve(timeStampFileName)
    if Files.notExists(filePath) then {
      Files.createFile(filePath)
      FileTime.from(Instant.MIN)
    } else {
      val when = Files.getLastModifiedTime(filePath)
      Files.setLastModifiedTime(filePath, FileTime.from(Instant.now()))
      when
    }
  }

  def prepareOptions(
    options: OnChangeCommand.Options
  ): OnChangeCommand.Options = {
    require(options.inputFile.isEmpty, "inputFile not used by this command")
    options
  }

  def dirHasChangedSince(
    dir: Path,
    minTime: FileTime
  ): Boolean = {
    val potentiallyChangedFiles = dir.toFile.listFiles().map(_.toPath)
    val maybeModified = for
      fName <- potentiallyChangedFiles
      timestamp = Files.getLastModifiedTime(fName)
      isModified = timestamp.compareTo(minTime) > 0
    yield { isModified }
    maybeModified.exists(x => x)
  }

}
