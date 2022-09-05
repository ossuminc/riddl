package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigReader
import scopt.OParser

import java.io.File
import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object RepeatCommand {

  val defaultMaxLoops: Int = 1024

  case class Options(
    command: String = "repeat",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None,
    refreshRate: FiniteDuration = 10.seconds,
    maxCycles: Int = defaultMaxLoops,
    interactive: Boolean = false
  ) extends CommandOptions
}

class RepeatCommand extends CommandPlugin[RepeatCommand.Options](
  "repeat") {
  import RepeatCommand.Options

  /**
   * Provide an scopt OParser for the commands options type, OPT
   *
   * @param log A logger to use for output (discouraged)
   * @return A pair: the OParser and the default values for OPT
   */
  override def getOptions(): (OParser[Unit, Options], Options) = {
    import builder.*
    cmd("repeat")
      .text(
        """This command supports the edit-build-check cycle. It doesn't end
          |until <max-cycles> has completed or EOF is reached on standard
          |input. During that time, the selected subcommands are repeated.
          |""".stripMargin)
      .action((_, c) => c.copy(command = pluginName))
      .children(
        arg[File]("config-file")
          .required()
          .action((f, c) => c.copy(inputFile = Some(f.toPath)))
          .text("The path to the configuration file that should be repeated"),
        opt[Option[String]]("target-command")
          .action { (cmd, opt) => opt.copy(targetCommand = cmd) }
          .text("The name of the command to select from the configuration file"),
        arg[FiniteDuration]("refresh-rate")
          .optional()
          .validate {
            case r if r.toMillis < 1000 =>
              Left("<refresh-rate> is too fast, minimum is 1 seconds")
            case r if r.toDays > 1 =>
              Left("<refresh-rate> is too slow, maximum is 1 day")
            case _ => Right(())
          }
          .action((r, c) => c.copy(refreshRate = r))
          .text(
            """Specifies the rate at which the <git-clone-dir> is checked
              |for updates so the process to regenerate the hugo site is
              |started""".stripMargin),
        arg[Int]("max-cycles")
          .optional()
          .validate {
            case x if x < 1 => Left("<max-cycles> can't be less than 1")
            case x if x > 1024 * 1024 => Left("<max-cycles> is too big")
            case _ => Right(())
          }
          .action((m, c) => c.copy(maxCycles = m))
          .text("""Limit the number of check cycles that will be repeated."""),
        opt[Unit] ('n', "interactive")
          .optional()
          .action((_, c) => c.copy(interactive = true))
          .text(
            """This option causes the repeat command to read from the standard
              |input and when it reaches EOF (Ctrl-D is entered) then it cancels
              |the loop to exit.""".stripMargin
          )
      ) -> Options()
  }


  override def getConfigReader(): ConfigReader[Options] = ???

  def allowCancel(options: Options): (Future[Boolean], () => Boolean) = {
    if (!options.interactive) {Future.successful(false) -> (() => false)}
    else {
      Interrupt.aFuture[Boolean] {
        while (
          Option(scala.io.StdIn.readLine("Type <Ctrl-D> To Exit:\n")).nonEmpty
        ) {}
        true
      }
    }
  }


  /**
   * Execute the command given the options. Error should be returned as
   * Left(messages) and not directly logged. The log is for verbose or debug
   * output
   *
   * @param options       The command specific options
   * @param commonOptions The options common to all commands
   * @param log           A logger for logging errors, warnings, and info
   * @return Either a set of Messages on error or a Unit on success
   */
  override def run(options: Options, commonOptions: CommonOptions, log: Logger): Either[Messages, Unit] = {
    val maxCycles = options.maxCycles
    val refresh = options.refreshRate
    val sleepTime = refresh.toMillis
    val (shouldQuit, cancel) = allowCancel(options)

    def userHasCancelled: Boolean = shouldQuit.isCompleted &&
      shouldQuit.value == Option(Success(true))

    var shouldContinue = true
    var i: Int = 0
    while (i < maxCycles && shouldContinue && !userHasCancelled ) {
      val result = CommandPlugin.runFromConfig(
        options.inputFile, options.targetCommand,
        commonOptions, log, pluginName
      ).map { _ =>
        if (!userHasCancelled) {
          cancel()
          shouldContinue = false
        } else {
          i += 1
          if (commonOptions.verbose) {
            println(s"Waiting for $refresh, cycle # $i of $maxCycles")
          }
          Thread.sleep(sleepTime)
        }
      }
      if (result.isLeft) {
        shouldContinue = false
      }
    }
    Right(())
  }
}