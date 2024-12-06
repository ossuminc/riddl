/*
 * Copyright 2019 Ossum, Inc.
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
import java.time.Instant
import java.time.temporal.{ChronoField, TemporalField}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


/** Handle processing of Language module's CommonOptions */
object CommonOptionsHelper:

  private def year: Int = Instant.now().getLong(ChronoField.YEAR).toInt
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

  lazy val commonOptionsParser: OParser[Unit, CommonOptions] = {
    val builder: OParserBuilder[CommonOptions] = OParser.builder[CommonOptions]

    import builder.*

    OParser.sequence(
      programName("riddlc"),
      head(blurb),
      opt[Unit]('t', name = "show-times")
        .optional()
        .action((_, c) => c.copy(showTimes = true))
        .text("Show parsing phase execution times"),
      opt[Unit]('I', name = "show-include-times")
        .optional()
        .action((_, c) => c.copy(showIncludeTimes = true))
        .text("Show parsing of included files execution times"),
      opt[Unit]('d', "dry-run")
        .optional()
        .action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose")
        .optional()
        .action((_, c) => c.copy(verbose = true))
        .text("Provide verbose output detailing actions taken by riddlc"),
      opt[Unit]('D', "debug")
        .optional()
        .action((_, c) => c.copy(debug = true))
        .text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', "quiet")
        .optional()
        .action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('a', "no-ansi-messages")
        .optional()
        .action((_, c) => c.copy(noANSIMessages = true))
        .text("Do not print messages with ANSI formatting"),
      opt[Unit]('w', name = "suppress-warnings")
        .optional()
        .action((_, c) =>
          c.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false,
            showUsageWarnings = false
          )
        )
        .text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings")
        .optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Suppress warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings")
        .optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Suppress warnings about questionable input style. "),
      opt[Unit]('u', name = "suppress-usage-warnings")
        .optional()
        .action((_, c) => c.copy(showUsageWarnings = false))
        .text("Suppress warnings about usage of definitions. "),
      opt[Unit]('i', name = "suppress-info-messages")
        .optional()
        .action((_, c) => c.copy(showInfoMessages = false))
        .text("Suppress information output"),
      opt[Unit]('w', name = "hide-warnings")
        .optional()
        .action((_, c) =>
          c.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false,
            showUsageWarnings = false
          )
        ),
      opt[Unit]('m', name = "hide-missing-warnings")
        .optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Hide warnings about things that are missing"),
      opt[Unit]('s', name = "hide-style-warnings")
        .optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Hide warnings about questionable input style. "),
      opt[Unit]('u', name = "hide-usage-warnings")
        .optional()
        .action((_, c) => c.copy(showUsageWarnings = false))
        .text("Hide warnings about usage of definitions. "),
      opt[Unit](name = "hide-info-messages")
        .optional()
        .action((_, c) => c.copy(showInfoMessages = false))
        .text("Hide information output"),
      opt[Boolean]('S', name = "sort-warnings-by-location")
        .optional()
        .action((_, c) => c.copy(sortMessagesByLocation = true))
        .text(
          "Print all messages sorted by the file name and line number in which they occur."
        ),
      opt[Boolean]('G', name = "group-messages-by-kind")
        .optional()
        .action((_, c) => c.copy(groupMessagesByKind = true))
        .text(
          "Print all messages by their severity kind"
        ),
      opt[Int]('x', name = "max-parallel-parsing")
        .optional()
        .action((v, c) => c.copy(maxParallelParsing = v))
        .text(
          "Controls the maximum number of include files that will be parsed in parallel"
        ),
      opt[Int](name = "max-include-wait")
        .optional()
        .action((v, c) => c.copy(maxIncludeWait = FiniteDuration(v, "seconds")))
        .text("Maximum time that parsing an include file will wait for it to complete"),
      opt[Boolean]("warnings-are-fatal")
        .optional()
        .action((_, c) => c.copy(warningsAreFatal = true))
        .text(
          "Makes validation warnings fatal to encourage code perfection"
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
    val showTimes = if obj.hasPath("show-times") then obj.getBoolean("show-times") else default.showTimes
    val showIncludeTimes =
      if obj.hasPath("show-include-times") then obj.getBoolean("show-include-times") else default.showIncludeTimes
    val verbose = if obj.hasPath("verbose") then obj.getBoolean("verbose") else default.verbose
    val dryRun = if obj.hasPath("dry-run") then obj.getBoolean("dry-run") else default.dryRun
    val quiet = if obj.hasPath("quiet") then obj.getBoolean("quiet") else default.quiet
    val debug = if obj.hasPath("debug") then obj.getBoolean("debug") else default.debug
    val noANSIMessages =
      if obj.hasPath("no-ansi-messages") then obj.getBoolean("no-ansi-messages") else default.noANSIMessages
    val sortMessagesByLocation =
      if obj.hasPath("sort-messages-by-location") then
        obj.getBoolean("sort-messages-by-location")
      else default.sortMessagesByLocation
    val groupMessagesByKind =
      if obj.hasPath("group-messages-by-kind") then
        obj.getBoolean("group-messages-by-kind")
      else
        default.groupMessagesByKind
    val suppressWarnings = if obj.hasPath("suppress-warnings") then Some(obj.getBoolean("suppress-warnings")) else None
    val suppressStyleWarnings =
      if obj.hasPath("suppress-style-warnings") then Some(obj.getBoolean("suppress-style-warnings")) else None
    val suppressMissingWarnings =
      if obj.hasPath("suppress-missing-warnings") then Some(obj.getBoolean("suppress-missing-warnings")) else None
    val suppressUsageWarnings =
      if obj.hasPath("suppress-usage-warnings") then Some(obj.getBoolean("suppress-usage-warnings")) else None
    val suppressInfoMessages =
      if obj.hasPath("suppress-info-messages") then Some(obj.getBoolean("suppress-info-messages")) else None
    val hideWarnings = if obj.hasPath("hide-warnings") then Some(obj.getBoolean("hide-warnings")) else None
    val hideStyleWarnings =
      if obj.hasPath("hide-style-warnings") then Some(obj.getBoolean("hide-style-warnings")) else None
    val hideMissingWarnings =
      if obj.hasPath("hide-missing-warnings") then Some(obj.getBoolean("hide-missing-warnings")) else None
    val hideUsageWarnings =
      if obj.hasPath("hide-usage-warnings") then Some(obj.getBoolean("hide-usage-warnings")) else None
    val hideInfoMessages =
      if obj.hasPath("hide-info-messages") then Some(obj.getBoolean("hide-info-messages")) else None
    val showWarnings =
      if obj.hasPath("show-warnings") then obj.getBoolean("show-warnings") else default.showWarnings
    val showStyleWarnings =
      if obj.hasPath("show-style-warnings") then obj.getBoolean("show-style-warnings") else default.showStyleWarnings
    val showMissingWarnings =
      if obj.hasPath("show-missing-warnings") then obj.getBoolean("show-missing-warnings")
      else default.showMissingWarnings
    val showUsageWarnings =
      if obj.hasPath("show-usage-warnings") then obj.getBoolean("show-usage-warnings") else default.showUsageWarnings
    val showInfoMessages =
      if obj.hasPath("show-info-messages") then obj.getBoolean("show-info-messages") else default.showInfoMessages
    val maxParallelParsing =
      if obj.hasPath("max-parallel-parsing") then obj.getInt("max-parallel-parsing") else default.maxParallelParsing
    val maxIncludeWait =
      if obj.hasPath("max-include-wait") then
        val duration = obj.getDuration("max-include-wait")
        val seconds = duration.toMillis
        FiniteDuration(seconds,TimeUnit.MILLISECONDS)
      else default.maxIncludeWait
    val warningsAreFatal =
      if obj.hasPath("warnings-are-fatal") then obj.getBoolean("warnings-are-fatal") else default.warningsAreFatal

    val shouldShowWarnings =
      suppressWarnings.map(!_).getOrElse(hideWarnings.map(!_).getOrElse(showWarnings))
    val shouldShowMissing =
      suppressMissingWarnings.map(!_).getOrElse(hideMissingWarnings.map(!_).getOrElse(showMissingWarnings))
    val shouldShowStyle =
      suppressStyleWarnings.map(!_).getOrElse(hideStyleWarnings.map(!_).getOrElse(showStyleWarnings))
    val shouldShowUsage =
      suppressUsageWarnings.map(!_).getOrElse(hideUsageWarnings.map(!_).getOrElse(showUsageWarnings))
    val shouldShowInfos =
      suppressInfoMessages.map(!_).getOrElse(hideInfoMessages.map(!_).getOrElse(showInfoMessages))
    CommonOptions(
      showTimes,
      showIncludeTimes,
      verbose,
      dryRun,
      quiet,
      shouldShowWarnings,
      shouldShowMissing,
      shouldShowStyle,
      shouldShowUsage,
      shouldShowInfos,
      debug,
      sortMessagesByLocation,
      groupMessagesByKind,
      noANSIMessages,
      maxParallelParsing,
      maxIncludeWait,
      warningsAreFatal
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

