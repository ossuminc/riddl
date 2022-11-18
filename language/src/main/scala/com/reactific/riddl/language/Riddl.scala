/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.TopLevelParser
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.SysLogger
import org.apache.commons.lang3.exception.ExceptionUtils

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import scala.util.control.NonFatal

case class CommonOptions(
  showTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true,
  showUnusedWarnings: Boolean = true,
  debug: Boolean = false,
  pluginsDir: Option[Path] = None,
  sortMessagesByLocation: Boolean = false,
  groupMessagesByKind: Boolean = true)

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  /** Runs a code block and returns its result, and prints its execution time to
    * stdout. Execution time is only written if `show` is set to `true`.
    *
    * e.g.
    *
    * timer("my-stage", true) { 1 + 1 } // 2
    *
    * prints: Stage 'my-stage': 0.000 seconds
    *
    * @param stage
    *   The name of the stage, is included in output message
    * @param show
    *   if `true`, then message is printed, otherwise not
    * @param logger
    *   The logger to which timing messages should be put out.
    * @param f
    *   the code block to execute
    * @return
    *   The result of running `f`
    */
  def timer[T](
    stage: String,
    show: Boolean = true,
    logger: Logger = SysLogger()
  )(f: => T
  ): T = { RiddlImpl.timer(Clock.systemUTC(), logger, stage, show)(f) }

  def parse(
    path: Path,
    options: CommonOptions
  ): Either[Messages, RootContainer] = {
    if (Files.exists(path)) {
      val input = new FileParserInput(path)
      parse(input, options)
    } else {
      Left(
        List(Messages.error(s"Input file `${path.toString} does not exist."))
      )
    }
  }

  def parse(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions()
  ): Either[Messages, RootContainer] = {
    timer("parse", options.showTimes) { TopLevelParser.parse(input) }
  }

  def validate(
    root: RootContainer,
    commonOptions: CommonOptions
  ): Validation.Result = {
    timer("validate", commonOptions.showTimes) {
      Validation.validate(root, commonOptions)
    }
  }

  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions
  ): Either[Messages, Validation.Result] = {
    parse(input, commonOptions).flatMap { root =>
      val result = validate(root, commonOptions)
      if (result.messages.isOnlyWarnings) { Right(result) }
      else { Left(result.messages) }
    }
  }

  def parseAndValidate(
    path: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Validation.Result] = {
    parseAndValidate(RiddlParserInput(path), commonOptions)
  }

  case class CategoryStats(
    count: Integer = 0,
    percentOfDefinitions: Double = 0.0d,
    averageMaturity: Double = 0.0d,
    totalMaturity: Integer = 0,
    percentComplete: Double = 0.0d,
    percentDocumented: Double = 0.0d) {
    override def toString: String = {
      s"#:$count($percentOfDefinitions%), maturity:$totalMaturity($averageMaturity%), complete: $percentComplete%, " +
        s"documented: $percentDocumented%"
    }
  }
  case class Stats(
    count: Long,
    term_count: Long,
    maximum_depth: Int,
    categories: Map[String, CategoryStats])

  def collectStats(
    inputFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Stats] = {
    parse(inputFile, commonOptions).flatMap { root =>
      try { Right(collectStats(root)) }
      catch {
        case NonFatal(x) => Left(errors(
            ExceptionUtils.getRootCauseStackTrace(x)
              .mkString(System.lineSeparator()),
            At(RiddlParserInput(inputFile), offset = 0)
          ))
      }
    }
  }

  def makeCategoryStats(
    stats: KindStats,
    all_stats: KindStats
  ): CategoryStats = {
    if (stats.count > 0) {
      val average_maturity = (stats.maturitySum.toFloat / stats.count)
      val percent_of_all = (stats.count.toDouble / all_stats.count) * 100.0d
      val percent_completed = (stats.completed.toDouble / stats.count) * 100.0d
      val percent_documented =
        (stats.documented.toDouble / stats.count) * 100.0d
      CategoryStats(
        stats.count,
        percent_of_all,
        average_maturity,
        stats.maturitySum,
        percent_completed,
        percent_documented
      )
    } else CategoryStats()
  }

  def collectStats(root: RootContainer): Stats = {
    val stats = Finder(root).generateStatistics()
    val count = stats.all_stats.count
    val percent_complete = (stats.all_stats.completed.toDouble / count) * 100.0d
    val averageMaturity =
      (stats.all_stats.maturitySum.toDouble / count) * 100.0d
    val percent_documented =
      (stats.all_stats.documented.toDouble / count) * 100.0d
    val all = CategoryStats(
      count,
      100.0d,
      averageMaturity,
      stats.all_stats.maturitySum,
      percent_complete,
      percent_documented
    )
    val adaptor = makeCategoryStats(stats.adaptorStats, stats.all_stats)
    val context = makeCategoryStats(stats.contextStats, stats.all_stats)
    val domain = makeCategoryStats(stats.domainStats, stats.all_stats)
    val entity = makeCategoryStats(stats.entityStats, stats.all_stats)
    val function = makeCategoryStats(stats.functionStats, stats.all_stats)
    val handler = makeCategoryStats(stats.handlerStats, stats.all_stats)
    val plant = makeCategoryStats(stats.plantStats, stats.all_stats)
    val processor = makeCategoryStats(stats.processorStats, stats.all_stats)
    val projection = makeCategoryStats(stats.projectionStats, stats.all_stats)
    val saga = makeCategoryStats(stats.sagaStats, stats.all_stats)
    val story = makeCategoryStats(stats.storyStats, stats.all_stats)
    val other = makeCategoryStats(stats.other_stats, stats.all_stats)
    Stats(
      count,
      stats.terms_count,
      stats.maximum_depth,
      categories = Map(
        "All" -> all,
        "Adaptor" -> adaptor,
        "Context" -> context,
        "Domain" -> domain,
        "Entity" -> entity,
        "Function" -> function,
        "Handler" -> handler,
        "Plant" -> plant,
        "Processor" -> processor,
        "Projection" -> projection,
        "Saga" -> saga,
        "Story" -> story,
        "Other" -> other
      )
    )
  }
}

/** Private implementation details which allow for more testability */
private[language] object RiddlImpl {

  /** Runs a code block and returns its result, while recording its execution
    * time, according to the passed clock. Execution time is written to `out`,
    * if `show` is set to `true`.
    *
    * e.g.
    *
    * timer(Clock.systemUTC(), System.out, "my-stage", true) { 1 + 1 } // 2
    *
    * prints: Stage 'my-stage': 0.000 seconds
    *
    * @param clock
    *   the clock that provides the start/end times to compute execution time
    * @param out
    *   the PrintStream to write execution time information to
    * @param stage
    *   The name of the stage, is included in output message
    * @param show
    *   if `true`, then message is printed, otherwise not
    * @param f
    *   the code block to execute
    *
    * @return
    *   The result of running `f`
    */
  def timer[T](
    clock: Clock,
    out: Logger,
    stage: String,
    show: Boolean
  )(f: => T
  ): T = {
    if (show) {
      val start = clock.millis()
      val result = f
      val stop = clock.millis()
      val delta = stop - start
      val seconds = delta / 1000
      val milliseconds = delta % 1000
      out.info(f"Stage '$stage': $seconds.$milliseconds%03d seconds")
      result
    } else { f }
  }
}
