package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.{ValidatingOptions, ValidationMessage}
import com.yoppworks.ossum.riddl.language.parsing.{FileParserInput, RiddlParserInput, TopLevelParser}

import java.nio.file.{Files, Path}
import java.time.Clock

case class ParsingOptions(
  showTimes: Boolean = false,
  logger: Option[Logger] = Some(SysLogger())) {
  def log: Logger = logger.getOrElse(SysLogger())
}

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
    options: ParsingOptions
  ): Option[RootContainer] = {
    if (Files.exists(path)) {
      val input = new FileParserInput(path)
      parse(input, options)
    } else {
      options.log.error(s"Input file `${path.toString} does not exist.")
      None
    }
  }

  def parse(
    input: RiddlParserInput,
    options: ParsingOptions
  ): Option[RootContainer] = {
    timer("parse", options.showTimes) {
      TopLevelParser.parse(input) match {
        case Left(errors) =>
          errors.map(_.format).foreach(options.log.error(_))
          options.log.info(s"Syntax Errors: ${errors.length}")
          None
        case Right(root) => Option(root)
      }
    }
  }

  def validate(
    root: RootContainer,
    options: ValidatingOptions
  ): Option[RootContainer] = {
    timer("validation", options.parsingOptions.showTimes) {
      val messages: Seq[ValidationMessage] = Validation.validate[RootContainer](root)
      val log = options.parsingOptions.log
      if (messages.nonEmpty) {
        val (warns, errs) = messages.partition(_.kind.isWarning)
        val (severe, errors) = errs.partition(_.kind.isSevereError)
        val missing = warns.filter(_.kind.isMissing)
        val style = warns.filter(_.kind.isStyle)
        val warnings = warns.filterNot(x => x.kind.isMissing | x.kind.isStyle)
        log.info(s"""Validation Warnings: ${warns.length}""")
        if (options.showWarnings) { warnings.map(_.format).foreach(log.warn(_)) }
        if (options.showMissingWarnings) { missing.map(_.format).foreach(log.warn(_)) }
        if (options.showStyleWarnings) { style.map(_.format).foreach(log.warn(_)) }
        log.info(s"""Validation Errors: ${errors.length} errors""")
        errors.map(_.format).foreach(log.error(_))
        log.info(s"""Severe Errors: ${errors.length} errors""")
        severe.map(_.format).foreach(log.severe(_))
        if (errs.nonEmpty) { None }
        else { Option(root) }
      } else { Option(root) }
    }
  }

  def parseAndValidate(
    input: RiddlParserInput,
    options: ValidatingOptions
  ): Option[RootContainer] = {
    parse(input, options.parsingOptions) match {
      case Some(root) => validate(root, options)
      case None       => None
    }
  }

  def parseAndValidate(
    path: Path,
    options: ValidatingOptions
  ): Option[RootContainer] = { parseAndValidate(RiddlParserInput(path), options) }
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
