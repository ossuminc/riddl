/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Utility for formatting RIDDL build information.
  * This provides a consistent way to display version and build info
  * across different tools (riddlc, IDE plugins, etc.)
  */
object InfoFormatter {

  /** Format build information as a human-readable string.
    * This method provides the same output as the `riddlc info` command.
    *
    * @return Formatted build information string
    */
  def formatInfo: String = {
    val lines = Seq(
      "About RIDDL:",
      s"           name: ${RiddlBuildInfo.name}",
      s"        version: ${RiddlBuildInfo.version}",
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
