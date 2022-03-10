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
import com.reactific.riddl.language.Validation.ValidationMessage
import com.reactific.riddl.language.parsing.{FileParserInput, RiddlParserInput, TopLevelParser}

import java.nio.file.{Files, Path}
import java.time.Clock

case class CommonOptions(
  showTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true
)

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  /** Runs a code block and returns its result, and prints its execution time to stdout. Execution
    * time is only written if `show` is set to `true`.
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
  def timer[T](stage: String, show: Boolean = true, logger: Logger = SysLogger())(f: => T): T = {
    RiddlImpl.timer(Clock.systemUTC(), logger, stage, show)(f)
  }

  def parse(
    path: Path,
    log: Logger,
    options: CommonOptions
  ): Option[RootContainer] = {
    if (Files.exists(path)) {
      val input = new FileParserInput(path)
      parse(input, log, options)
    } else {
      log.error(s"Input file `${path.toString} does not exist.")
      None
    }
  }

  def parse(
    input: RiddlParserInput,
    log: Logger,
    options: CommonOptions
  ): Option[RootContainer] = {
    timer("parse", options.showTimes) {
      TopLevelParser.parse(input) match {
        case Left(errors) =>
          errors.map(_.format).foreach(log.error(_))
          log.info(s"Syntax Errors: ${errors.length}")
          None
        case Right(root) => Option(root)
      }
    }
  }

  def validate(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
  ): Option[RootContainer] = {
    timer("validation", commonOptions.showTimes) {
      val messages: Seq[ValidationMessage] =
        Validation.validate(root, commonOptions)
      if (messages.nonEmpty) {
        val (warns, errs) = messages.partition(_.kind.isWarning)
        val (severe, errors) = errs.partition(_.kind.isSevereError)
        val missing = warns.filter(_.kind.isMissing)
        val style = warns.filter(_.kind.isStyle)
        val warnings = warns.filterNot(x => x.kind.isMissing | x.kind.isStyle)
        log.info(s"""Validation Warnings: ${warns.length}""")
        if (commonOptions.showWarnings) { warnings.map(_.format).foreach(log.warn(_)) }
        if (commonOptions.showMissingWarnings) { missing.map(_.format).foreach(log.warn(_)) }
        if (commonOptions.showStyleWarnings) { style.map(_.format).foreach(log.warn(_)) }
        log.info(s"""Validation Errors: ${errors.length}""")
        errors.map(_.format).foreach(log.error(_))
        log.info(s"""Severe Errors: ${severe.length}""")
        severe.map(_.format).foreach(log.severe(_))
        if (errs.nonEmpty) { None }
        else { Option(root) }
      } else { Option(root) }
    }
  }

  def parseAndValidate(
    input: RiddlParserInput,
    logger: Logger,
    commonOptions: CommonOptions,
  ): Option[RootContainer] = {
    parse(input, logger, commonOptions) match {
      case Some(root) => validate(root, logger, commonOptions)
      case None       => None
    }
  }

  def parseAndValidate(
    path: Path,
    logger: Logger,
    commonOptions: CommonOptions,
  ): Option[RootContainer] = {
    parseAndValidate(RiddlParserInput(path), logger, commonOptions)
  }
}

/** Private implementation details which allow for more testability */
private[language] object RiddlImpl {

  /** Runs a code block and returns its result, while recording its execution time, according to the
    * passed clock. Execution time is written to `out`, if `show` is set to `true`.
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
