/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext}

import org.ekrich.config.Config
import scopt.OParser

import java.io.File
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object ValidateCommand {
  final val cmdName = "validate"

  /** The severity LADDER `--fail-on` accepts, loosest to strictest.
    *
    * **`style`, `usage`, `missing` and `completeness` are deliberately NOT here.** They are
    * warning CLASSES, not levels: every one of them answers `isWarning`, so ranking them below
    * `warning` made `--fail-on warning` pass on a model full of style warnings while
    * `--fail-on style` failed — the opposite of what both names promise. Anyone wanting to fail on
    * style writes `--fail-on warning`, because a style finding IS a warning; the classes are for
    * switching output on and off, which the global `-w`/`-s`/`-m`/`-u` flags already do.
    */
  private[commands] val Severities: Seq[String] = Seq("info", "warning", "error", "severe")

  case class Options(
    inputFile: Option[Path] = None,
    failOn: Option[String] = None,
    json: Boolean = false
  ) extends CommandOptions {
    def command: String = cmdName
  }
}

/** Validate a model, and ALWAYS say what was checked.
  *
  * **A command that prints nothing cannot be told apart from one that never ran**, and riddl-models
  * proved that three times in a single day, each time producing a confident wrong zero: `timeout`
  * does not exist on macOS (exit 127, empty output, "0 findings"); the option parser rejected a
  * value form and terminated (grep counted 0 messages); and `2>/dev/null` hid the diagnostics
  * entirely, since they go to stderr. In all three the correct observation was "this command did
  * not do what you think", and in all three the observable was identical to a perfect corpus.
  *
  * `find` already prints its count unconditionally for exactly this reason. This is the command
  * whose zero releases are staked on, so it does too.
  *
  * **The summary names the ENABLED warning classes**, which is not decoration: 187 of
  * riddl-models' 188 `.conf` files switch style and usage warnings OFF, so running through a
  * `.conf` is quietly the LENIENT gate while a direct `riddlc validate <file>` is the strict one.
  * That is the opposite of what anyone assumes about a build gate, and nothing in the output hinted
  * at it.
  *
  * The summary goes to STDOUT because it is the command's product; diagnostics stay on stderr.
  */
class ValidateCommand(using pc: PlatformContext)
    extends Command[ValidateCommand.Options](ValidateCommand.cmdName) {
  import ValidateCommand.{Options, Severities}

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(ValidateCommand.cmdName)
      .children(
        arg[File]("input-file").action((f, o) => o.copy(inputFile = Some(f.toPath))),
        opt[String]("fail-on")
          .validate(s =>
            if Severities.contains(s.toLowerCase) then success
            else failure(s"--fail-on must be one of: ${Severities.mkString(", ")}")
          )
          .action((s, o) => o.copy(failOn = Some(s.toLowerCase)))
          .text(s"Exit non-zero if any message is at or above this severity " +
            s"(${Severities.mkString(", ")})"),
        opt[Unit]("json")
          .action((_, o) => o.copy(json = true))
          .text("Emit diagnostics as a JSON array on stdout instead of the human summary")
      )
      .text("Validate a model and report a one-line summary of what was checked")
      -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile =
      if obj.hasPath("input-file") then Option(Path.of(obj.getString("input-file"))) else None
    val failOn = if obj.hasPath("fail-on") then Option(obj.getString("fail-on")) else None
    val json = obj.hasPath("json") && obj.getBoolean("json")
    Options(inputFile, failOn, json)
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPathSafe(inputFile.toString).map {
        case Left(messages) =>
          // A file that cannot be read has no counts to report beyond the messages themselves,
          // but it must still not be silent.
          report(options, 0, messages)
          Left(messages)
        case Right(rpi) =>
          // `shouldFailOnError = false` so the RESULT is in hand either way and the summary can be
          // printed. The failing exit is then reproduced below, so this is not a loosening: a model
          // with errors still returns Left, exactly as before.
          Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
            case Left(messages) =>
              report(options, 0, messages)
              Left(messages)
            case Right(result) =>
              report(options, result.symbols.parentage.size, result.messages)
              if result.messages.hasErrors then Left(result.messages)
              else if failsThreshold(options.failOn, result.messages) then Left(result.messages)
              else Right(result)
      }
      Await.result(future, 10.seconds)
    }
  }

  /** Emit whichever shape was asked for. Both go to STDOUT, because both are the product. */
  private def report(options: Options, definitions: Int, messages: Messages): Unit =
    if options.json then emitJson(messages) else summarize(definitions, messages)

  /** The machine-readable shape: one object per diagnostic, on stdout, and nothing else.
    *
    * **Printed with `println`, never `pc.log`**, which prefixes `[info]` and would produce a stream
    * whose lines are not JSON -- invisible to the eye and fatal to a pipe. The same trap `dump
    * --json` hit and the reason its count moved to stderr.
    *
    * An empty model emits `[]` rather than nothing: a consumer must be able to tell "validated
    * clean" from "the command never ran", which is the whole reason this command always speaks.
    *
    * `rule` is null only for a diagnostic that has not been given an id. Every rule riddl emits
    * has one, so in practice this is a parse-time message from a path that predates the ids.
    */
  private def emitJson(messages: Messages): Unit = {
    val records = messages.map { m =>
      val loc = m.loc
      val fields = scala.collection.mutable.LinkedHashMap[String, ujson.Value](
        "rule" -> m.ruleCode.map(ujson.Str(_)).getOrElse(ujson.Null),
        "severity" -> ujson.Str(m.kind.toString),
        "message" -> ujson.Str(m.message),
        "file" -> ujson.Str(loc.source.origin),
        "line" -> ujson.Num(loc.line),
        "col" -> ujson.Num(loc.col)
      )
      if m.context.nonEmpty then fields("context") = ujson.Str(m.context)
      // The suggestion is stripped by the Accumulator unless --provide-tips is on, so its
      // presence here reflects what the user asked for rather than what the rule defines.
      if m.suggestion.nonEmpty then fields("suggestion") = ujson.Str(m.suggestion)
      ujson.Obj.from(fields.toSeq)
    }
    // `System.out.println`, NOT a bare `println`. A bare one is `Console.println`, whose stream is
    // a THREAD-LOCAL initialised at class load -- and this runs inside the future, on an executor
    // thread, so it would write to the real stdout even while a test has redirected it. Same
    // object in production, invisibly different under capture.
    System.out.println(ujson.write(ujson.Arr.from(records), indent = 2))
  }

  /** Does any message reach the `--fail-on` threshold? */
  private def failsThreshold(failOn: Option[String], messages: Messages): Boolean =
    failOn.exists { level =>
      val floor = Severities.indexOf(level)
      messages.exists(m => severityRank(m) >= floor)
    }

  /** Where a message sits on the ladder. Every warning class ranks as `warning`, which is the whole
    * point -- a style finding is a warning, not something beneath one.
    */
  private def severityRank(m: Messages.Message): Int =
    if m.isSevere then Severities.indexOf("severe")
    else if m.isError then Severities.indexOf("error")
    else if m.isWarning then Severities.indexOf("warning")
    else Severities.indexOf("info")

  /** One line, always, on stdout.
    *
    * The breakdown is by CLASS, and the total is not their sum plus itself: `isWarning` is true for
    * style, missing and usage as well, so adding those to a naive warning count double-counts every
    * one of them. `warnings` here is the total, with the classes shown beside it.
    */
  private def summarize(definitions: Int, messages: Messages): Unit = {
    def count(p: Messages.Message => Boolean): Int = messages.count(p)
    val errors = count(m => m.isError || m.isSevere)
    val warnings = count(_.isWarning)
    val byClass = Seq(
      "style" -> count(_.isStyle),
      "missing" -> count(_.isMissing),
      "usage" -> count(_.isUsage),
      "completeness" -> count(_.isCompleteness)
    ).collect { case (name, n) if n > 0 => s"$n $name" }
    val o = pc.options
    val enabled = Seq(
      "style" -> o.showStyleWarnings,
      "usage" -> o.showUsageWarnings,
      "missing" -> o.showMissingWarnings,
      "completeness" -> o.showCompletenessWarnings
    ).collect { case (name, true) => name }
    val classes =
      if !o.showWarnings then "warnings off"
      else if enabled.isEmpty then "all warning classes off"
      else enabled.mkString(", ") + " on"
    def plural(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"
    val breakdown = if byClass.isEmpty then "" else byClass.mkString(" (", ", ", ")")
    pc.stdoutln(
      s"${plural(definitions, "definition")} checked, ${plural(errors, "error")}, " +
        s"${plural(warnings, "warning")}$breakdown  [$classes]"
    )
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] =
    super.loadOptionsFrom(configFile).map(options => resolveInputFileToConfigFile(options, configFile))

  override protected def replaceInputFile(options: Options, inputFile: Path): Options =
    options.copy(inputFile = Some(inputFile))
}
