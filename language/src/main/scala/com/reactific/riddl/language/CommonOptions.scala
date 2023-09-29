/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language
import java.nio.file.Path

case class CommonOptions(
  showTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  // FIXME: Add this and its reader, etc: showInfoMessages: Boolean = true,
  // TODO: Add a "maxParallelParsing" option to limit parallel included file parsing (not implemented yet)
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true,
  showUsageWarnings: Boolean = true,
  debug: Boolean = false,
  pluginsDir: Option[Path] = None,
  sortMessagesByLocation: Boolean = false,
  groupMessagesByKind: Boolean = true,
  noANSIMessages: Boolean = false
)

object CommonOptions {
  def empty: CommonOptions = CommonOptions()
  def noWarnings: CommonOptions = CommonOptions(showWarnings = false)
  def noMinorWarnings: CommonOptions =
    CommonOptions(showMissingWarnings = false, showStyleWarnings = false, showUsageWarnings = false)
}
