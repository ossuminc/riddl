/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language
import java.nio.file.Path
import scala.concurrent.duration.{Duration,DurationInt}

case class CommonOptions(
  showTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true,
  showUsageWarnings: Boolean = true,
  showInfoMessages: Boolean = true,
  debug: Boolean = false,
  pluginsDir: Option[Path] = None,
  sortMessagesByLocation: Boolean = false,
  groupMessagesByKind: Boolean = true,
  noANSIMessages: Boolean = false,
  maxParallelParsing: Int = Runtime.getRuntime.availableProcessors,
  maxIncludeWait: Duration = 1.second,
  warningsAreFatal: Boolean = false
)

object CommonOptions {
  def empty: CommonOptions = CommonOptions()
  def noWarnings: CommonOptions = CommonOptions(showWarnings = false)
  def noMinorWarnings: CommonOptions =
    CommonOptions(showMissingWarnings = false, showStyleWarnings = false, showUsageWarnings = false)
}
