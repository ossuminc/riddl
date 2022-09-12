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
  debug: Boolean = false,
  pluginsDir: Option[Path] = None
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
  ): Either[Messages,RootContainer] = {
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
    options: CommonOptions
  ): Either[Messages,RootContainer] = {
    timer("parse", options.showTimes) {
      TopLevelParser.parse(input)
    }
  }

  def validate(
    root: RootContainer,
    commonOptions: CommonOptions
  ): Either[Messages,RootContainer] = {
    timer("validate", commonOptions.showTimes) {
      Validation.validate(root, commonOptions) match {
        case list: Messages if list.isEmpty =>
          Right(root)
        case list: Messages =>
          Left(list)
      }
    }
  }


  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions
  ): Either[Messages,RootContainer] = {
    parse(input, commonOptions).flatMap { root =>
      validate(root, commonOptions)
    }
  }

  def parseAndValidate(
    path: Path,
    commonOptions: CommonOptions
  ): Either[Messages,RootContainer] = {
    parseAndValidate(RiddlParserInput(path), commonOptions)
  }

  type Stats = SortedMap[String, String]
  def collectStats(inputFile: Path, commonOptions: CommonOptions):
  Either[Messages,Stats] = {
    parse(inputFile, commonOptions).map { root =>
      val statistics = Finder(root).generateStatistics()
      SortedMap(
        "Definitions" -> statistics.definitions.toString,
        "Incomplete" -> statistics.incomplete.toString,
        "Maximum Depth" -> statistics.maximum_depth.toString,
        "Missing Documentation" -> statistics.missing_documentation.toString,
        "Total Maturity"-> statistics.total_maturity.toString,
        "Percent Complete" -> statistics.percent_complete.toString,
        "Percent Documented" -> statistics.percent_documented.toString,
        "Average Maturity" -> statistics.average_maturity.toString
      )
    }
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
