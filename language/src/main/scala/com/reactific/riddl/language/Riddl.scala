/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.{FileParserInput, RiddlParserInput, TopLevelParser}
import com.reactific.riddl.utils.{Logger, SysLogger}

import java.nio.file.{Files, Path}
import java.time.Clock
import scala.collection.SortedMap

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
  sortMessagesByLocation: Boolean = false
)

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
   * The name of the stage, is included in output message
   * @param show
   * if `true`, then message is printed, otherwise not
   * @param logger
   * The logger to which timing messages should be put out.
   * @param f
   * the code block to execute
   * @return
   * The result of running `f`
   */
  def timer[T](
    stage: String,
    show: Boolean = true,
    logger: Logger = SysLogger()
  )(
    f: => T
  ): T = {RiddlImpl.timer(Clock.systemUTC(), logger, stage, show)(f)}

  def parse(
    path: Path,
    options: CommonOptions
  ): Either[Messages, RootContainer] = {
    if (Files.exists(path)) {
      val input = new FileParserInput(path)
      parse(input, options)
    } else {
      Left(List(
        Messages.error(s"Input file `${path.toString} does not exist.")
      ))
    }
  }

  def parse(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions()
  ): Either[Messages, RootContainer] = {
    timer("parse", options.showTimes) {
      TopLevelParser.parse(input)
    }
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
  ): Either[Messages,Validation.Result] = {
    parse(input, commonOptions).flatMap { root =>
      val result = validate(root, commonOptions)
      if (result.messages.isOnlyWarnings) {
        Right(result)
      } else {
        Left(result.messages)
      }
    }
  }

  def parseAndValidate(
    path: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Validation.Result] = {
    parseAndValidate(RiddlParserInput(path), commonOptions)
  }

  type Stats = SortedMap[String, String]

  def collectStats(inputFile: Path, commonOptions: CommonOptions):
  Either[Messages, Stats] = {
    parse(inputFile, commonOptions).flatMap { root =>
      collectStats(root)
    }
  }

  def collectStats(root: RootContainer):
  Either[Messages, Stats] = {
    val stats = Finder(root).generateStatistics()
    val completed = stats.definitions - stats.incomplete
    val documented = stats.definitions - stats.missing_documentation
    val empty = SortedMap.empty[String, String]
    Right(SortedMap(
      "Definitions" -> stats.definitions.toString,
      "Incomplete" -> stats.incomplete.toString,
      "Maximum Depth" -> stats.maximum_depth.toString,
      "Missing Documentation" -> stats.missing_documentation.toString,
      "Total Maturity" -> stats.total_maturity.toString
    ) ++ (if (stats.definitions > 0) {
      val percent_complete =
        (completed.toFloat / stats.definitions.toFloat) * 100.0F
      val percent_documented =
        (documented.toFloat / stats.definitions.toFloat) * 100.0F
      val average_maturity =
        (stats.total_maturity.toFloat / stats.definitions.toFloat)
      SortedMap(
        "Percent Complete" -> percent_complete.toString,
        "Percent Documented" -> percent_documented.toString,
        "Average Maturity" -> average_maturity.toString,
      )
    } else {empty}) ++
      (if (stats.adaptorStats.count > 0) {
        val average_maturity =
          (stats.adaptorStats.maturitySum.toFloat / stats.adaptorStats.count)
        SortedMap(
          "Adaptor Count" -> stats.adaptorStats.count.toString,
          "Adaptor Total Maturity" -> stats.adaptorStats.maturitySum.toString,
          "Adaptor Average Maturity" -> average_maturity.toString
        )
      } else {empty}) ++
      (if (stats.contextStats.count > 0) {
        val average_maturity =
          (stats.contextStats.maturitySum.toFloat / stats.contextStats.count)
        SortedMap(
          "Context Count" -> stats.contextStats.count.toString,
          "Context Total Maturity" -> stats.contextStats.maturitySum.toString,
          "Context Average Maturity" -> average_maturity.toString
        )
      } else {empty}) ++
      (if (stats.domainStats.count > 0) {
        val average_maturity =
          (stats.domainStats.maturitySum.toFloat / stats.domainStats.count)
        SortedMap(
          "Domain Count" -> stats.domainStats.count.toString,
          "Domain Total Maturity" -> stats.domainStats.maturitySum.toString,
          "Domain Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.entityStats.count > 0) {
        val average_maturity =
          (stats.entityStats.maturitySum.toFloat / stats.entityStats.count)
        SortedMap(
          "Entity Count" -> stats.entityStats.count.toString,
          "Entity Total Maturity" -> stats.entityStats.maturitySum.toString,
          "Entity Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.functionStats.count > 0) {
        val average_maturity =
          (stats.functionStats.maturitySum.toFloat / stats.functionStats.count)
        SortedMap(
          "Function Count" -> stats.functionStats.count.toString,
          "Function Total Maturity" -> stats.functionStats.maturitySum.toString,
          "Function Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.handlerStats.count > 0) {
        val average_maturity =
          (stats.handlerStats.maturitySum.toFloat / stats.handlerStats.count)
        SortedMap(
          "Handler Count" -> stats.handlerStats.count.toString,
          "Handler Total Maturity" -> stats.handlerStats.maturitySum.toString,
          "Handler Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.plantStats.count > 0) {
        val average_maturity =
          (stats.plantStats.maturitySum.toFloat / stats.plantStats.count)
        SortedMap(
          "Plant Count" -> stats.plantStats.count.toString,
          "Plant Total Maturity" -> stats.plantStats.maturitySum.toString,
          "Plant Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.processorStats.count > 0) {
        val average_maturity =
          (stats.processorStats.maturitySum.toFloat / stats.processorStats.count)
        SortedMap(
          "Processor Count" -> stats.processorStats.count.toString,
          "Processor Total Maturity" -> stats.processorStats.maturitySum.toString,
          "Processor Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.projectionStats.count > 0) {
        val average_maturity =
          (stats.projectionStats.maturitySum.toFloat / stats.projectionStats.count)
        SortedMap(
          "Projection Count" -> stats.projectionStats.count.toString,
          "Projection Total Maturity" -> stats.projectionStats.maturitySum.toString,
          "Projection Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.sagaStats.count > 0) {
        val average_maturity =
          (stats.sagaStats.maturitySum.toFloat / stats.sagaStats.count)
        SortedMap(
          "Saga Count" -> stats.sagaStats.count.toString,
          "Saga Total Maturity" -> stats.sagaStats.maturitySum.toString,
          "Saga Average Maturity" -> average_maturity.toString
        )
      } else empty) ++
      (if (stats.storyStats.count > 0) {
        val average_maturity =
          (stats.storyStats.maturitySum.toFloat / stats.storyStats.count)
        SortedMap(
          "Story Count" -> stats.storyStats.count.toString,
          "Story Total Maturity" -> stats.storyStats.maturitySum.toString,
          "Story Average Maturity" -> average_maturity.toString
        )
      } else empty))
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
