/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import com.ossuminc.riddl.utils.StringHelpers
import com.ossuminc.riddl.utils.{PlatformContext, RiddlBuildInfo, SysLogger}
import org.ekrich.config.*
import scopt.*

import java.nio.file.Path
import java.time.{Clock, ZoneId}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


/** Handle processing of Language module's CommonOptions */
object CommonOptionsHelper:

  private def year: Int = Clock.systemUTC().instant().atZone(ZoneId.systemDefault()).getYear
  private val start: String = RiddlBuildInfo.startYear
  val blurb: String =
    s"""RIDDL Compiler © $start-$year Ossum Inc. All rights reserved."
       |Version: ${RiddlBuildInfo.version}
       |
       |This program parses, validates and translates RIDDL sources to other kinds
       |of documents. RIDDL is a language for system specification based on Domain
       |Drive Design, Reactive Architecture, and distributed system principles.
       |
       |""".stripMargin

  private inline def show_times = "show-times"
  private inline def show_include_times = "show-include-times"
  private inline def dry_run = "dry-run"
  private inline def verbose = "verbose"
  private inline def debug = "debug"
  private inline def quiet = "quiet"
  private inline def no_ansi_messages = "no-ansi-messages"
  private inline def show_warnings = "show-warnings"
  private inline def show_missing_warnings = "show-missing-warnings"
  private inline def show_style_warnings = "show-style-warnings"
  private inline def show_usage_warnings = "show-usage-warnings"
  private inline def show_info_messages = "show-info-messages"
  private inline def sort_messages_by_location = "sort-messages-by-location"
  private inline def group_messages_by_kind = "group-messages-by-kind"
  private inline def max_parallel_parsing = "max-parallel-parsing"
  private inline def max_include_wait = "max-include-wait"
  private inline def warnings_are_fatal = "warnings-are-fatal"
  private inline def auto_generate_bast = "auto-generate-bast"

  lazy val commonOptionsParser: OParser[Unit, CommonOptions] = {
    val builder: OParserBuilder[CommonOptions] = OParser.builder[CommonOptions]

    import builder.*

    OParser.sequence(
      programName("riddlc"),
      head(blurb),
      opt[Unit]('t', name = show_times)
        .optional()
        .action((_, c) => c.copy(showTimes = true))
        .text("Show parsing phase execution times"),
      opt[Unit]('I', name = show_include_times)
        .optional()
        .action((_, c) => c.copy(showIncludeTimes = true))
        .text("Show parsing of included files execution times"),
      opt[Unit]('d', dry_run)
        .optional()
        .action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', verbose)
        .optional()
        .action((_, c) => c.copy(verbose = true))
        .text("Provide verbose output detailing actions taken by riddlc"),
      opt[Unit]('D', debug)
        .optional()
        .action((_, c) => c.copy(debug = true))
        .text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', quiet)
        .optional()
        .action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('a', no_ansi_messages)
        .optional()
        .action((_, c) => c.copy(noANSIMessages = true))
        .text("Do not print messages with ANSI formatting"),
      opt[Boolean]('w', name = show_warnings)
        .optional()
        .action((s, c) =>
          c.copy(
            showWarnings = s,
            showMissingWarnings = s,
            showStyleWarnings = s,
            showUsageWarnings = s
          )
        )
        .text("Suppress all warning messages so only errors are shown"),
      opt[Boolean]('m', name = show_missing_warnings)
        .optional()
        .action((s, c) => c.copy(showMissingWarnings = s))
        .text("Suppress warnings about things that are missing"),
      opt[Boolean]('s', name = show_style_warnings)
        .optional()
        .action((s, c) => c.copy(showStyleWarnings = s))
        .text("Suppress warnings about questionable input style. "),
      opt[Boolean]('u', name = show_usage_warnings)
        .optional()
        .action((s, c) => c.copy(showUsageWarnings = s))
        .text("Suppress warnings about usage of definitions. "),
      opt[Boolean]('i', name = show_info_messages)
        .optional()
        .action((s, c) => c.copy(showInfoMessages = s))
        .text("Suppress information output"),
      opt[Boolean]('S', name = sort_messages_by_location)
        .optional()
        .action((_, c) => c.copy(sortMessagesByLocation = true))
        .text(
          "Print all messages sorted by the file name and line number in which they occur."
        ),
      opt[Boolean]('G', name = group_messages_by_kind)
        .optional()
        .action((_, c) => c.copy(groupMessagesByKind = true))
        .text(
          "Print all messages by their severity kind"
        ),
      opt[Int]('x', name = max_parallel_parsing)
        .optional()
        .action((v, c) => c.copy(maxParallelParsing = v))
        .text(
          "Controls the maximum number of include files that will be parsed in parallel"
        ),
      opt[Int](name = max_include_wait)
        .optional()
        .action((v, c) => c.copy(maxIncludeWait = FiniteDuration(v, "seconds")))
        .text("Maximum time that parsing an include file will wait for it to complete"),
      opt[Boolean](warnings_are_fatal)
        .optional()
        .action((_, c) => c.copy(warningsAreFatal = true))
        .text(
          "Makes validation warnings fatal to encourage code perfection"
        ),
      opt[Unit]('B', auto_generate_bast)
        .optional()
        .action((_, c) => c.copy(autoGenerateBAST = true))
        .text(
          "Automatically generate .bast files next to .riddl files after parsing (like Python's .pyc)"
        )
    )
  }

  def parseCommonOptions(
    args: Array[String]
  )(using io: PlatformContext): (Option[CommonOptions], Array[String]) = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError: Option[Boolean] = Option(false)

      override def renderingMode: RenderingMode.TwoColumns.type =
        RenderingMode.TwoColumns
    }

    val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
      override def displayToOut(msg: String): Unit = { io.log.info(msg) }

      override def displayToErr(msg: String): Unit = { io.log.error(msg) }

      override def reportError(msg: String): Unit = { io.log.error(msg) }

      override def reportWarning(msg: String): Unit = { io.log.warn(msg) }

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneArgs = args.map(_.trim).filter(_.nonEmpty)
    val options = saneArgs.takeWhile(_.startsWith("-"))
    val remainingOptions = saneArgs.dropWhile(_.startsWith("-"))
    val (common, effects1) = OParser.runParser[CommonOptions](
      commonOptionsParser,
      options,
      com.ossuminc.riddl.utils.CommonOptions.default,
      setup
    )
    OParser.runEffects(effects1, dontTerminate)
    common -> remainingOptions
  }

  private def noBool = Option.empty[Boolean]

  private def commonOptionsReader(config: Config): CommonOptions =
    val default = CommonOptions()
    val obj = config.getObject("common").toConfig
    val showTimes = if obj.hasPath(show_times) then obj.getBoolean(show_times) else default.showTimes
    val showIncludeTimes =
      if obj.hasPath(show_include_times) then obj.getBoolean(show_include_times) else default.showIncludeTimes
    val verboseV = if obj.hasPath(verbose) then obj.getBoolean(verbose) else default.verbose
    val dryRunV = if obj.hasPath(dry_run) then obj.getBoolean(dry_run) else default.dryRun
    val quietV = if obj.hasPath(quiet) then obj.getBoolean(quiet) else default.quiet
    val debugV = if obj.hasPath(debug) then obj.getBoolean(debug) else default.debug
    val noANSIMessages =
      if obj.hasPath(no_ansi_messages) then obj.getBoolean(no_ansi_messages) else default.noANSIMessages
    val sortMessagesByLocation =
      if obj.hasPath(sort_messages_by_location) then
        obj.getBoolean(sort_messages_by_location)
      else default.sortMessagesByLocation
    val groupMessagesByKind =
      if obj.hasPath(group_messages_by_kind) then
        obj.getBoolean(group_messages_by_kind)
      else
        default.groupMessagesByKind
    val showWarnings = if obj.hasPath(show_warnings) then obj.getBoolean(show_warnings) else default.showWarnings
    val showStyleWarnings =
      if obj.hasPath(show_style_warnings) then obj.getBoolean(show_style_warnings) else default.showStyleWarnings
    val showMissingWarnings =
      if obj.hasPath(show_missing_warnings) then obj.getBoolean(show_missing_warnings) else default.showMissingWarnings
    val showUsageWarnings =
      if obj.hasPath(show_usage_warnings) then obj.getBoolean(show_usage_warnings) else default.showUsageWarnings
    val showInfoMessages =
      if obj.hasPath(show_info_messages) then obj.getBoolean(show_info_messages) else default.showInfoMessages
    val maxParallelParsing =
      if obj.hasPath(max_parallel_parsing) then obj.getInt(max_parallel_parsing) else default.maxParallelParsing
    val maxIncludeWait =
      if obj.hasPath(max_include_wait) then
        val duration = obj.getDuration(max_include_wait)
        val seconds = duration.toMillis
        FiniteDuration(seconds,TimeUnit.MILLISECONDS)
      else default.maxIncludeWait
    val warningsAreFatal =
      if obj.hasPath(warnings_are_fatal) then obj.getBoolean(warnings_are_fatal) else default.warningsAreFatal
    val autoGenerateBAST =
      if obj.hasPath(auto_generate_bast) then obj.getBoolean(auto_generate_bast) else default.autoGenerateBAST

    CommonOptions(
      showTimes,
      showIncludeTimes,
      verboseV,
      dryRunV,
      quietV,
      showWarnings,
      showMissingWarnings,
      showStyleWarnings,
      showUsageWarnings,
      showInfoMessages,
      debugV,
      sortMessagesByLocation,
      groupMessagesByKind,
      noANSIMessages,
      maxParallelParsing,
      maxIncludeWait,
      warningsAreFatal,
      autoGenerateBAST
    )
  end commonOptionsReader

  def loadCommonOptions(configFile: Path)(using pc: PlatformContext): Either[Messages, CommonOptions] =
    val options: ConfigParseOptions = ConfigParseOptions.defaults
      .setAllowMissing(true)
      .setOriginDescription(configFile.getFileName.toString)
    try {
      val config = ConfigFactory.parseFile(configFile.toFile, options)
      if pc.options.verbose then {
        pc.log.info(s"Read command options from $configFile")
      }
      val opt = commonOptionsReader(config)
      if pc.options.debug then {
        println(StringHelpers.toPrettyString(options, 1, Some("Loaded common options:")))
      }
      Right(opt)
    } catch {
      case NonFatal(xcptn) =>
        Left(
          errors(s"Failed to load options from $configFile because:\n" +
            xcptn.getClass.getSimpleName + ": " + xcptn.getMessage
          )
        )
    }
  end loadCommonOptions
end CommonOptionsHelper

