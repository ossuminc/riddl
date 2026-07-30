/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Utility for formatting RIDDL build information. This provides a consistent way to display
  * version and build info across different tools (riddlc, IDE plugins, etc.)
  */
object InfoFormatter {

  /** Format build information as a human-readable string. This method provides the same output as
    * the `riddlc info` command.
    *
    * @return
    *   Formatted build information string
    */
  def formatInfo: String =
    s"$formatBuildInfo\n\n${ThirdPartyNotices.formatted}"

  /** The build information ALONE, without the third-party notices.
    *
    * Callers that append platform-specific lines of their own need this, so the notices can stay at
    * the very END of the output rather than being buried mid-way. `riddlc info` adds JVM and OS
    * details, so it composes this with [[ThirdPartyNotices.formatted]] itself.
    *
    * @return
    *   Formatted build information, no attribution block
    */
  def formatBuildInfo: String = {
    val lines = Seq(
      "Build information about RIDDL:",
      s"        version: ${RiddlBuildInfo.version}",
      s"     git commit: ${RiddlBuildInfo.gitCommit}",
      s"  documentation: ${RiddlBuildInfo.projectHomepage}",
      s"      copyright: ${RiddlBuildInfo.copyright}",
      s"       built at: ${RiddlBuildInfo.builtAtString}",
      s"       licenses: ${RiddlBuildInfo.licenses}",
      s"   organization: ${RiddlBuildInfo.organizationName}",
      s"  scala version: ${RiddlBuildInfo.scalaVersion}",
      s"    sbt version: ${RiddlBuildInfo.sbtVersion}"
    )
    lines.mkString("\n")
  }
}
