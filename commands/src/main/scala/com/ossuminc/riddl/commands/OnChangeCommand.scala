/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages.{Messages, errors}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.PlatformContext
import org.ekrich.config.*
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

object OnChangeCommand {
  final val cmdName: String = "onchange"
  final val defaultMaxLoops = 1024
  case class Options(
    configFile: Path = Path.of("."),
    watchDirectory: Path = Path.of("."),
    targetCommand: String = ParseCommand.cmdName,
    refreshRate: FiniteDuration = 10.seconds,
    maxCycles: Int = defaultMaxLoops,
    interactive: Boolean = false
  ) extends CommandOptions {
    def command: String = cmdName
    def inputFile: Option[Path] = None
  }
}

class OnChangeCommand(using pc: PlatformContext)
    extends Command[OnChangeCommand.Options](OnChangeCommand.cmdName) {
  import OnChangeCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("onchange")
        .children(
          arg[File]("config-file")
            .required()
            .action((f, opts) => opts.copy(configFile = f.toPath))
            .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
          arg[File]("watch-directory")
            .required()
            .action((f, opts) => opts.copy(watchDirectory = f.toPath))
            .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
          arg[String]("target-command")
            .required()
            .action { (cmd, opt) =>
              opt.copy(targetCommand = cmd)
            }
            .text(RepeatingCommandText.targetCommand),
          arg[FiniteDuration]("refresh-rate")
            .optional()
            .validate {
              case r if r < RepeatingCommandText.minRefreshRate =>
                Left(RepeatingCommandText.refreshRateTooFast)
              case r if r > RepeatingCommandText.maxRefreshRate =>
                Left(RepeatingCommandText.refreshRateTooSlow)
              case _ => Right(())
            }
            .action((r, c) => c.copy(refreshRate = r))
            .text(RepeatingCommandText.refreshRate),
          arg[Int]("max-cycles")
            .optional()
            .validate {
              case x if x < RepeatingCommandText.minCycles =>
                Left(RepeatingCommandText.tooFewCycles)
              case x if x > RepeatingCommandText.maxCyclesLimit =>
                Left(RepeatingCommandText.tooManyCycles)
              case _ => Right(())
            }
            .action((m, c) => c.copy(maxCycles = m))
            .text(RepeatingCommandText.maxCycles)
        )
        .text(
          """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the configured command is run""".stripMargin
        )
    ) -> Options()
  }

  override def interpretConfig(config: Config): Options =
    val obj = config.getObject(commandName).toConfig
    val configFile = obj.getString("config-file")
    require(configFile.nonEmpty, "'config-file' requires a non-empty value")
    val watchDirectory = obj.getString("watch-directory")
    require(watchDirectory.nonEmpty, "'watch-directory' requires a non-empty value")
    val targetCommand = obj.getString("target-command")
    require(targetCommand.nonEmpty, "'target-command' requires a non-empty value")
    val refreshRate: FiniteDuration =
      FiniteDuration(obj.getDuration("refresh-rate", TimeUnit.SECONDS), TimeUnit.SECONDS)
    val maxCycles = obj.getInt("max_cycles")
    OnChangeCommand.Options(
      Path.of(configFile),
      Path.of(watchDirectory),
      targetCommand,
      refreshRate,
      maxCycles
    )
  end interpretConfig

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(configFile = inputFile) }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }

  /** Execute the command given the options. Error should be returned as Left(messages) and not
    * directly logged. The log is for verbose or debug output
    *
    * @param options
    *   The command specific options
    * @param outputDirOverride
    *   The value of an override for the outputDir option
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    Left(errors("Not Implemented"))
  }

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
