/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.commands.find.FindEditor
import com.ossuminc.riddl.language.{Fix, Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext}

import org.ekrich.config.Config
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Path}
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
    json: Boolean = false,
    fix: Boolean = false,
    fixRule: Option[String] = None,
    fixDryRun: Boolean = false
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
          .text("Emit diagnostics as a JSON array on stdout instead of the human summary"),
        opt[Unit]("fix")
          .action((_, o) => o.copy(fix = true))
          .text("Apply every rule that carries a mechanical fix, then re-validate"),
        opt[String]("fix-rule")
          .validate(r =>
            if RuleId.parse(r).exists(_.mechanicalFix.isDefined) then success
            else
              failure(
                s"--fix-rule must name a rule with a mechanical fix; these have one: " +
                  RuleId.mechanicalReplacements.keys.toSeq.sorted.mkString(", ")
              )
          )
          .action((r, o) => o.copy(fix = true, fixRule = Some(r)))
          .text("Apply only this rule's fix (implies --fix)"),
        // NOT `--dry-run`. That name is a GLOBAL option, and `Commands.handleCommandRun`
        // short-circuits on it -- logging "Would have executed..." WITHOUT invoking the command --
        // so a `validate --dry-run` could never reach this code at all. `find` needed its own
        // `-dry-run` for exactly the same reason.
        opt[Unit]("fix-dry-run")
          .action((_, o) => o.copy(fix = true, fixDryRun = true))
          .text("Show the diff --fix would apply, and write nothing (implies --fix)")
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
    val fix = obj.hasPath("fix") && obj.getBoolean("fix")
    val fixRule = if obj.hasPath("fix-rule") then Option(obj.getString("fix-rule")) else None
    val fixDryRun = obj.hasPath("fix-dry-run") && obj.getBoolean("fix-dry-run")
    Options(inputFile, failOn, json, fix || fixRule.isDefined || fixDryRun, fixRule, fixDryRun)
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
              if options.fix then applyFixes(inputFile, options, result)
              else
                report(options, result.symbols.parentage.size, result.messages)
                if result.messages.hasErrors then Left(result.messages)
                else if failsThreshold(options.failOn, result.messages) then Left(result.messages)
                else Right(result)
      }
      Await.result(future, 10.seconds)
    }
  }

  /** Applies every mechanical fix the model's own diagnostics ask for.
    *
    * **A rule carries its own fix** ([[RuleId.mechanicalFix]]) rather than a fixer holding a table
    * of rules, so a rule and its remedy cannot drift apart -- the same reasoning that made
    * `DeprecationCode.all` derived rather than hand-listed.
    *
    * Only rules whose fix is a pure SPAN REPLACEMENT qualify, and that set is deliberately smaller
    * than the auto-fixable one. `shape-keyword` rewrites `flow X is` to `processor X as flow is`,
    * an insertion somewhere other than the reported span, and `state-is-record` may need to INSERT
    * a keyword where nothing stands. Applying those as span swaps would corrupt source, so they are
    * absent rather than approximated.
    *
    * Writing goes through the SAME gate as `find -replace`: overlaps refused, applied back to
    * front, re-parsed and re-validated, and every file restored unless the model got no worse.
    */
  private def applyFixes(
    inputFile: Path,
    options: Options,
    before: PassesResult
  ): Either[Messages, PassesResult] = {
    // Every diagnostic is classified, so the report can say what it did NOT fix and why. Their
    // design note is the reason this is not optional: "a codemod that silently leaves 40 sites is
    // worse than one that fixes none".
    val fixable = scala.collection.mutable.ListBuffer.empty[(Messages.Message, RuleId, Fix)]
    // Grouped by REASON, listing the rules -- not one line per rule. The reason text is identical
    // for every rule lacking a fix, so a line each turned a 20-finding model into 11 lines of the
    // same sentence and buried the one that mattered.
    val skipped = scala.collection.mutable.LinkedHashMap.empty[String, scala.collection.mutable.LinkedHashSet[String]]
    val skipCount = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    def skip(reason: String, rule: String): Unit =
      skipped.getOrElseUpdate(reason, scala.collection.mutable.LinkedHashSet.empty) += rule
      skipCount(reason) = skipCount.getOrElse(reason, 0) + 1

    before.messages.foreach { m =>
      m.ruleId match
        case None => skip("no rule id, so no rule can carry a fix", "(unidentified)")
        case Some(rule) =>
          if options.fixRule.exists(_ != rule.code) then
            skip(s"filtered out by --fix-rule ${options.fixRule.get}", rule.code)
          else
            rule.mechanicalFix match
              case None =>
                skip(
                  "no mechanical fix: needs a judgement call, or a rewrite outside the " +
                    "reported span",
                  rule.code
                )
              case Some(fix) =>
                FindEditor.fileOfSource(m.loc.source) match
                  case None    => skip("not in a file this run can edit", rule.code)
                  case Some(_) => fixable.append((m, rule, fix))
    }

    def reportSkips(label: String): Unit =
      if skipCount.nonEmpty then
        pc.stdoutln(s"$label: ${skipCount.values.sum} not fixed:")
        skipCount.toSeq.sortBy(-_._2).foreach { case (reason, n) =>
          val rules = skipped(reason).toSeq.sorted
          val named =
            if rules == Seq("(unidentified)") then ""
            else s" [${rules.mkString(", ")}]"
          pc.stdoutln(s"  $n x $reason$named")
        }

    if fixable.isEmpty then
      // Says so out loud rather than exiting silently: "nothing to fix" and "the command did not
      // run" must not share an observable, which is this command's whole premise.
      val scope = options.fixRule.map(r => s" for rule '$r'").getOrElse("")
      pc.stdoutln(s"validate --fix: nothing to fix$scope")
      reportSkips("validate --fix")
      Right(before)
    else
      // `fileOfSource`, NOT `Path.of(loc.source.origin)`: origin is the SHORT name error messages
      // render, so treating it as a path works only when the cwd happens to be the model's own
      // directory. `find -replace` shipped with exactly that bug.
      val edits = fixable.toSeq.flatMap { case (m, rule, fix) =>
        FindEditor
          .fileOfSource(m.loc.source)
          .map { file =>
            // A COMPUTED fix is applied against the text it matched, which the message's own span
            // identifies. `quoted-constant-literal` (`"5"` -> `5`) is only expressible this way.
            val matched = m.loc.source.data.slice(m.loc.offset, m.loc.endOffset)
            FindEditor.Edit(file, m.loc.offset, m.loc.endOffset, fix(matched), rule.code)
          }
      }
      FindEditor.plan(edits) match
        case Left(problems) =>
          Left(Messages.errors(("validate --fix: nothing written" +: problems).mkString("\n")))
        case Right(byFile) =>
          val originals = byFile.keys.map(f => f -> Files.readString(f)).toMap
          val rewritten = byFile.map { case (f, es) => f -> FindEditor.apply(originals(f), es) }.toMap
          val byRule = edits.groupBy(_.what).view.mapValues(_.size).toSeq.sorted
          val summary = byRule.map { case (rule, n) => s"$rule x$n" }.mkString(", ")
          if options.fixDryRun then
            FindEditor.showDiff(originals, rewritten)
            pc.stdoutln(
              s"validate --fix-dry-run: would apply ${edits.size} fix(es) in " +
                s"${rewritten.size} file(s): $summary (nothing written)"
            )
            reportSkips("validate --fix-dry-run")
            Right(before)
          else
            FindEditor.applyVerified(
              originals,
              rewritten,
              before.messages.count(_.isError),
              () => loadAndValidate(inputFile),
              "validate --fix"
            ) match
              case Left(messages) => Left(messages)
              case Right(after) =>
                pc.stdoutln(
                  s"validate --fix: applied ${edits.size} fix(es) in ${rewritten.size} " +
                    s"file(s): $summary"
                )
                reportSkips("validate --fix")
                Right(after)
  }

  /** Re-parse and re-validate from disk, for the post-rewrite check. */
  private def loadAndValidate(inputFile: Path): Either[Messages, PassesResult] = {
    implicit val ec: ExecutionContext = pc.ec
    Await.result(
      RiddlParserInput.fromPathSafe(inputFile.toString).map {
        case Left(messages) => Left(messages)
        case Right(rpi)     => Riddl.parseAndValidate(rpi, shouldFailOnError = false)
      },
      10.seconds
    )
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
        // SEVERITY and CLASS are separate fields, deliberately. `kind` conflates them -- a
        // consumer handed `"MissingWarning"` has to know riddl's taxonomy to learn that it is a
        // WARNING, which is the one thing a triage script needs first. Severity answers "how bad",
        // class answers "what kind", and the -w/-s/-m/-u flags switch on the class.
        "severity" -> ujson.Str(severityOf(m)),
        "class" -> ujson.Str(classOf_(m)),
        // The raw kind is kept so nothing that already reads it breaks, and so the two derived
        // fields can always be traced back to what produced them.
        "kind" -> ujson.Str(m.kind.toString),
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

  /** Where the message sits on the severity ladder: how bad it is. */
  private def severityOf(m: Messages.Message): String =
    if m.isSevere then "severe"
    else if m.isError then "error"
    else if m.isWarning then "warning"
    else "info"

  /** What KIND of finding it is -- the axis the `-w`/`-s`/`-m`/`-u` flags switch on.
    *
    * Every warning class also answers `isWarning`, which is why severity alone cannot carry this:
    * a style finding IS a warning, and a consumer filtering on severity would lose the distinction
    * entirely. `general` is the plain Warning with no sub-class.
    */
  private def classOf_(m: Messages.Message): String =
    if m.isStyle then "style"
    else if m.isMissing then "missing"
    else if m.isUsage then "usage"
    else if m.isCompleteness then "completeness"
    else if m.isDeprecation then "deprecation"
    else if m.isTip then "tip"
    else if m.isWarning then "general"
    else severityOf(m)

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
