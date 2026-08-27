/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{Interrupt, PlatformContext}
import org.ekrich.config.*
import scopt.OParser

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.Success

object RepeatCommand {

  final val cmdName = "repeat"
  val defaultMaxLoops: Int = 1024

  case class Options(
    inputFile: Option[Path] = None,
    targetCommand: String = "",
    refreshRate: FiniteDuration = 10.seconds,
    maxCycles: Int = defaultMaxLoops,
    interactive: Boolean = false
  ) extends CommandOptions {
    def command: String = cmdName
  }
}

class RepeatCommand(using pc: PlatformContext)
    extends Command[RepeatCommand.Options](RepeatCommand.cmdName) {
  import RepeatCommand.Options

  /** Provide a scopt OParser for the commands options type, OPT
    *
    * @return
    *   A pair: the OParser and the default values for OPT
    */
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(RepeatCommand.cmdName)
      .text("""This command supports the edit-build-check cycle. It doesn't end
              |until <max-cycles> has completed or EOF is reached on standard
              |input. During that time, the selected subcommands are repeated.
              |""".stripMargin)
      .children(
        arg[File]("config-file")
          .required()
          .action((f, c) => c.copy(inputFile = Some(f.toPath)))
          .text("The path to the configuration file that should be repeated"),
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
            case x if x < RepeatingCommandText.minCycles => Left(RepeatingCommandText.tooFewCycles)
            case x if x > RepeatingCommandText.maxCyclesLimit =>
              Left(RepeatingCommandText.tooManyCycles)
            case _ => Right(())
          }
          .action((m, c) => c.copy(maxCycles = m))
          .text(RepeatingCommandText.maxCycles),
        opt[Unit]('n', "interactive")
          .optional()
          .action((_, c) => c.copy(interactive = true))
          .text(
            """This option causes the repeat command to read from the standard
              |input and when it reaches EOF (Ctrl-D is entered) then it cancels
              |the loop to exit.""".stripMargin
          )
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options =
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("config-file"))
    val targetCommand = obj.getString("target-command")
    val refreshRate: FiniteDuration =
      FiniteDuration(obj.getDuration("refresh-rate", TimeUnit.SECONDS), TimeUnit.SECONDS)
    val maxCycles = obj.getInt("max-cycles")
    val interactive = obj.getBoolean("interactive")
    Options(Some(inputFile), targetCommand, refreshRate, maxCycles, interactive)
  end interpretConfig

  /** Execute the command given the options. Error should be returned as Left(messages) and not
    * directly logged. The log is for verbose or debug output
    *
    * @param options
    *   The command specific options
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val maxCycles = options.maxCycles
    val refresh = options.refreshRate
    val sleepTime = refresh.toMillis
    val (shouldQuit, cancel) = Interrupt.allowCancel(options.interactive)

    def userHasCancelled: Boolean = shouldQuit.isCompleted &&
      shouldQuit.value == Option(Success(true))

    var shouldContinue = true
    var i: Int = 0
    while i < maxCycles && shouldContinue && !userHasCancelled do {
      val result = Commands
        .runFromConfig(
          options.inputFile,
          options.targetCommand,
          "repeat"
        )
        .map { _ =>
          if !userHasCancelled then {
            cancel.map(_.apply())
            shouldContinue = false
          } else {
            i += 1
            if pc.options.verbose then {
              pc.log.info(s"Waiting for $refresh, cycle # $i of $maxCycles")
            }
            Thread.sleep(sleepTime)
          }
        }
      if result.isLeft then { shouldContinue = false }
    }
    Right(PassesResult())
  }
}
