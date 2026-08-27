/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}
import org.ekrich.config.*
import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.NonFatal

object Commands:

  /** Convert a string and some [[com.ossuminc.riddl.utils.CommonOptions]] into either a
    * [[com.ossuminc.riddl.command.Command]] or some
    * [[com.ossuminc.riddl.language.Messages.Messages]] Note that the
    * [[com.ossuminc.riddl.command.CommandOptions]] will be passed to the command when you run it.
    *
    * @param name
    *   The name of the command to be converted
    * @return
    */
  def loadCommandNamed(
    name: String
  )(using io: PlatformContext): Either[Messages, Command[?]] =
    if io.options.verbose then io.log.info(s"Loading command: $name") else ()
    name match
      case "about"     => Right(AboutCommand())
      case "advise"    => Right(AdviseCommand())
      case "bastify"   => Right(BastifyCommand())
      case "dump"      => Right(DumpCommand())
      case "find"      => Right(FindCommand())
      case "flatten"   => Right(FlattenCommand())
      case "from"      => Right(FromCommand())
      case "help"      => Right(HelpCommand())
      case "info"      => Right(InfoCommand())
      case "onchange"  => Right(OnChangeCommand())
      case "parse"     => Right(ParseCommand())
      case "prettify"  => Right(PrettifyCommand())
      case "repeat"    => Right(RepeatCommand())
      case "stats"     => Right(StatsCommand())
      case "unbastify" => Right(UnbastifyCommand())
      case "validate"  => Right(ValidateCommand())
      case "version"   => Right(VersionCommand())
      case _           => Left(errors(s"No command found for '$name'"))
    end match
  end loadCommandNamed

  /** Probably the easiest way to run a command if you're familiar with the command line options and
    * still get the [[com.ossuminc.riddl.language.Messages.Messages]] or
    * [[com.ossuminc.riddl.passes.PassesResult]] objects out of it.
    *
    * @param args
    *   An [[Array[String]] of arguments, one argument per array element. This should follow the
    *   same pattern as by the `riddlc` command line options (run `riddlc help` to discover that
    *   syntax). Unlike `riddlc`, the first argument must be the name of the command to run. The
    *   common options cannot occur ahead of it and are provided by the `commonOptions` argument to
    *   this function.
    * @return
    *   One of two things:
    *   - [[scala.util.Left]] of [[com.ossuminc.riddl.language.Messages.Messages]] if the command
    *     fails and the contained messages, a [[List]] of
    *     [[com.ossuminc.riddl.language.Messages.Messages]], that explain the errors
    *   - [[scala.util.Right]] of [[com.ossuminc.riddl.passes.PassesResult]] to provide the details
    *     of what the [[com.ossuminc.riddl.passes.Pass]]es that run produced.
    */
  def runCommandWithArgs(
    args: Array[String]
  )(using io: PlatformContext): Either[Messages, PassesResult] =
    require(args.nonEmpty, "Empty argument list provided")
    val name = args.head
    val result = CommandLoader.loadCommandNamed(name).flatMap { cmd =>
      cmd.run(args)
    }
    if io.options.verbose then
      val rc = if result.isRight then "yes" else "no"
      io.log.info(s"Ran: ${args.mkString(" ")}: success=$rc")
    end if
    result
  end runCommandWithArgs

  def runCommandNamed(
    name: String,
    optionsPath: Path,
    outputDirOverride: Option[Path] = None
  )(using io: PlatformContext): Either[Messages, PassesResult] =
    if io.options.verbose then io.log.info(s"About to run $name with options from $optionsPath")
    end if
    CommandLoader.loadCommandNamed(name).flatMap { cmd =>
      cmd.loadOptionsFrom(optionsPath).flatMap { opts =>
        cmd.run(opts, outputDirOverride) match
          case Left(errors) =>
            if io.options.debug then {
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
    configFile: Path
  )(using io: PlatformContext): Either[Messages, Seq[String]] =
    try
      val config = ConfigFactory.parseFile(configFile.toFile)
      val names = config.root.keySet().asScala.toSeq
      if io.options.verbose then
        io.log.info(s"Found candidate commands in $configFile: ${names.mkString(" ")}")
      Right(names)
    catch
      case ce: ConfigException =>
        val message = s"Errors while reading $configFile:\n" +
          ce.getClass.getSimpleName + ": " + ce.getMessage
        Left(errors(message))
    end try
  end loadCandidateCommands

  /** An easy way to run the `from` command which loads commands and their options from a `.config`
    * file and uses them as defaults. The [[com.ossuminc.riddl.utils.CommonOptions]] specification
    * in the `.config` file can be overridden with the `commonOptions` argument.
    *
    * @param configFile
    *   An optional [[java.nio.file.Path]] for the config file. Relative or full paths are fine.
    * @param targetCommand
    *   The command to run. This must match a config setting in the `configFile` that provides the
    *   arguments for that command.
    * @param commandName
    *   The name of the command that is invoking this method, if it matters
    * @return
    *   One of two things:
    *   - [[scala.util.Left]] of [[com.ossuminc.riddl.language.Messages.Messages]], which is a list
    *     of [[com.ossuminc.riddl.language.Messages.Message]], that explain why it failed.
    *   - [[scala.util.Right]] of [[com.ossuminc.riddl.passes.PassesResult]] to provide the details
    *     of what the [[com.ossuminc.riddl.passes.Pass]]es that run produced.
    */
  def runFromConfig(
    configFile: Option[Path],
    targetCommand: String,
    commandName: String
  )(using io: PlatformContext): Either[Messages, PassesResult] =
    val result = CommandOptions.withInputFile[PassesResult](configFile, commandName) { path =>
      loadCandidateCommands(path).flatMap { names =>
        if names.contains(targetCommand) then
          runCommandNamed(targetCommand, path) match
            case Left(errors) =>
              if io.options.debug then
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
    result
  end runFromConfig

  private def handleCommandResult(
    result: Either[Messages, PassesResult]
  )(using io: PlatformContext): Int =
    result match
      case Right(passesResult: PassesResult) =>
        if io.options.quiet then io.log.info(io.log.summary)
        else logMessages(passesResult.messages)
        if io.options.warningsAreFatal && passesResult.messages.hasWarnings then 1 else 0
      case Left(messages) =>
        if io.options.quiet then highestSeverity(messages) + 1
        else {
          logMessages(messages) + 1
        }
    end match
  end handleCommandResult

  private def handleCommandRun(
    remaining: Array[String]
  )(using io: PlatformContext): Int =
    if remaining.isEmpty then
      io.log.error("No command argument was provided")
      1
    else if io.options.dryRun then
      io.log.info(s"Would have executed: ${remaining.mkString(" ")}")
      0
    else if remaining.contains(CorpusFlag) then runCorpus(remaining)
    else
      val result = runCommandWithArgs(remaining)
      handleCommandResult(result)
  end handleCommandRun

  private final val CorpusFlag = "--corpus"

  /** `riddlc <command> --corpus <dir>` -- one process over many models.
    *
    * Every corpus-wide operation in riddl-models is a shell loop starting a process per model: 188
    * for a warning sweep, 190 to regenerate `.bast`, 188 x 3 for the round-trip check, run a dozen
    * times in a session. The work per model is small, so process start dominates -- and the cost is
    * entirely avoidable because the models are independent.
    *
    * **Independent by design, not merged.** Each model is validated in isolation and the results
    * aggregated; nothing is combined into one AST, because models do not share a namespace.
    *
    * **A failing model does not abort the rest.** That is the acceptance criterion that matters: a
    * sweep exists to find every problem, and stopping at the first one makes it report a prefix of
    * the truth.
    *
    * **The total is riddlc's, not the caller's.** riddl-models' scripts each accumulate their own
    * denominator across invocations, and getting a denominator right is precisely where they keep
    * going wrong -- three of their nine scripted defects were a confident number computed over
    * nothing. Printing `N models` here removes that arithmetic from the consumer.
    *
    * Discovery is every `.conf` beneath the root. A `.conf` IS an entry point by definition; an
    * earlier draft filtered to those whose basename matches their directory, which is riddl-models'
    * house convention and would have silently skipped their two pattern-example models, whose
    * conf is named example.conf inside a differently-named directory.
    */
  private def runCorpus(remaining: Array[String])(using io: PlatformContext): Int =
    val idx = remaining.indexOf(CorpusFlag)
    val root = remaining.lift(idx + 1).getOrElse(".")
    val command = remaining.take(idx).headOption.getOrElse("validate")
    val confs = findConfFiles(root)
    if confs.isEmpty then
      io.log.error(s"No .conf entry points found beneath '$root'")
      1
    else
      var failed = 0
      confs.foreach { conf =>
        val result = runCommandWithArgs(Array("from", conf, command))
        result match
          case Right(_) => ()
          case Left(messages) =>
            failed += 1
            // Per-model detail so a failure names the model without re-running the sweep.
            io.log.error(s"$conf: ${messages.count(_.isError)} error(s)")
        end match
      }
      // The corpus total is the command's PRODUCT, so stdout -- diagnostics stayed on stderr.
      io.stdoutln(
        s"${confs.size} model${if confs.size == 1 then "" else "s"}, " +
          s"$failed failed, ${confs.size - failed} ok"
      )
      if failed > 0 then 1 else 0
  end runCorpus

  /** Every `.conf` beneath `root`, skipping build output. */
  private def findConfFiles(root: String): Seq[String] =
    val start = java.nio.file.Path.of(root)
    if !java.nio.file.Files.isDirectory(start) then Seq.empty
    else
      import scala.jdk.CollectionConverters.*
      val stream = java.nio.file.Files.walk(start)
      try
        stream
          .iterator()
          .asScala
          .filter(p => p.toString.endsWith(".conf"))
          .filterNot(p => p.toString.contains("/target/"))
          .map(_.toString)
          .toSeq
          .sorted
      finally stream.close()
  end findConfFiles

  def runMainForTest(args: Array[String])(using
    io: PlatformContext
  ): Either[Messages, PassesResult] =
    try
      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match
        case Some(commonOptions) =>
          io.withOptions(commonOptions) { _ =>
            if remaining.isEmpty then Left(List(error("No command argument was provided")))
            else runCommandWithArgs(remaining)
          }
        case None =>
          Left(List(error("Option parsing failed, terminating.")))
      end match
    catch
      case NonFatal(exception) =>
        Left(List(severe("Exception Thrown:", exception, At.empty)))
  end runMainForTest

  def runMain(args: Array[String])(using pc: PlatformContext): Int =
    try

      val (common, remaining) = CommonOptionsHelper.parseCommonOptions(args)
      common match
        case Some(commonOptions) =>
          pc.withOptions[Int](commonOptions) { _ =>
            handleCommandRun(remaining)
          }
        case None =>
          // arguments are bad, error message will have been displayed
          pc.log.info("Option parsing failed, terminating.")
          1
      end match
    catch
      case NonFatal(exception) =>
        pc.log.severe("Exception Thrown:", exception)
        SevereError.severity + 1
  end runMain

  def parseCommandOptions(
    args: Array[String]
  )(using io: PlatformContext): Either[Messages, CommandOptions] =
    require(args.nonEmpty)
    val result = CommandLoader.loadCommandNamed(args.head)
    result match
      case Right(cmd) =>
        cmd.parseOptions(args) match {
          case Some(options) => Right(options)
          case None          => Left(errors("RiddlOption parsing failed"))
        }
      case Left(messages) => Left(messages)
    end match
  end parseCommandOptions
