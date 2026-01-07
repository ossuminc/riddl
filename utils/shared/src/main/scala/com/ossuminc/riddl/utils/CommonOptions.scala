/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.scalajs.js.annotation.JSExportTopLevel

/** The common options available to any pass. Common options occur before the command name on the <code>riddlc</code>
  * command line
  *
  * @param showTimes
  *   Controls whether pass timing is printed out
  * @param showIncludeTimes
  *   Controls whether include processing timing is printed out
  * @param verbose
  *   Controls whether verbose steps taken are printed out (useful to implementors of riddlc)
  * @param dryRun
  *   Controls whether everything should be done except the requested action (useful with verbose)
  * @param quiet
  *   Controls whether riddlc should not print anything out but just do the requested action (opposite of dryRun)
  * @param showWarnings
  *   Controls whether any warnings, of any kind, are printed out
  * @param showMissingWarnings
  *   Controls whether any missing Warnings are printed out
  * @param showStyleWarnings
  *   Controls whether any style Warnings are printed out
  * @param showUsageWarnings
  *   Controls whether any usage Warnings are printed out
  * @param showInfoMessages
  *   Controls whether any informative messages are printed out
  * @param debug
  *   Controls whether debug output should be printed out (this goes into the iterative data that verbose doesn't print)
  * @param sortMessagesByLocation
  *   When set to true, the messages put out will be sorted by their location in the model (file, line, column)
  * @param groupMessagesByKind
  *   When set to true, the messages put out will be grouped by their kind and sorted with the most severe first.
  * @param noANSIMessages
  *   When set to true, ANSI coloring is not used in the output
  * @param maxParallelParsing
  *   Provides the maximum number of threads to use when parsing in parallel. The default is the number of CPU cores on
  *   the machine the program is running on.
  * @param maxIncludeWait
  *   Provides the maximum amount of time that the parser should wait for a parallel include to finish processing. The
  *   default is 5 seconds.
  * @param warningsAreFatal
  *   When set to true, any warnings are handled as if they were errors and terminate riddlc with a non-zero status
  */
@JSExportTopLevel("CommonOptions")
case class CommonOptions(
  showTimes: Boolean = false,
  showIncludeTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true,
  showUsageWarnings: Boolean = true,
  showInfoMessages: Boolean = true,
  debug: Boolean = false,
  sortMessagesByLocation: Boolean = false,
  groupMessagesByKind: Boolean = false,
  noANSIMessages: Boolean = false,
  maxParallelParsing: Int = Runtime.getRuntime.availableProcessors,
  maxIncludeWait: FiniteDuration = 10.second,
  warningsAreFatal: Boolean = false
)

@JSExportTopLevel("CommonOptions$")
object CommonOptions {
  def empty: CommonOptions = CommonOptions()
  def default: CommonOptions = CommonOptions()
  def noANSIMessages: CommonOptions = CommonOptions(noANSIMessages = true)
  def noWarnings: CommonOptions = empty.copy(showWarnings = false)
  def noMinorWarnings: CommonOptions = empty.copy(
    showMissingWarnings = false,
    showStyleWarnings = false,
    showUsageWarnings = false
  )
}
