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
package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.RiddlBuildInfo
import com.reactific.riddl.utils.SysLogger
import scopt.DefaultOEffectSetup
import scopt.DefaultOParserSetup
import scopt.OParser
import scopt.OParserBuilder
import scopt.OParserSetup
import scopt.RenderingMode

import java.io.File
import java.util.Calendar

/** Handle processing of Language module's CommonOptions */
object CommonOptionsHelper {

  val year: Int = Calendar.getInstance().get(Calendar.YEAR)
  val start: String = RiddlBuildInfo.startYear
  val blurb: String =
    s"""RIDDL Compiler © $start-$year Ossum Inc. All rights reserved."
       |Version: ${RiddlBuildInfo.version}
       |
       |This program parses, validates and translates RIDDL sources to other kinds
       |of documents. RIDDL is a language for system specification based on Domain
       |Drive Design, Reactive Architecture, and distributed system principles.
       |
       |""".stripMargin

  val commonOptionsParser: OParser[Unit, CommonOptions] = {
    val builder: OParserBuilder[CommonOptions] = OParser.builder[CommonOptions]

    import builder.*

    OParser.sequence(
      programName("riddlc"),
      head(blurb),
      opt[Unit]('t', name = "show-times").optional()
        .action((_, c) => c.copy(showTimes = true))
        .text("Show compilation phase execution times "),
      opt[Unit]('d', "dry-run").optional()
        .action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose").optional()
        .action((_, c) => c.copy(verbose = true))
        .text("Provide verbose output detailing riddlc's actions"),
      opt[Unit]('D', "debug").optional().action((_, c) => c.copy(debug = true))
        .text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', "quiet").optional().action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('w', name = "suppress-warnings").optional().action((_, c) =>
        c.copy(
          showWarnings = false,
          showMissingWarnings = false,
          showStyleWarnings = false,
          showUnusedWarnings = false
        )
      ).text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings").optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings").optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Show warnings about questionable input style. "),
      opt[Unit]('u', name = "suppress-unused-warnings").optional()
        .action((_, c) => c.copy(showUnusedWarnings = false))
        .text("Show warnings about questionable input style. "),
      opt[File]('P', name = "plugins-dir").optional()
        .action((file, c) => c.copy(pluginsDir = Some(file.toPath)))
        .text("Load riddlc command extension plugins from this directory.")
    )
  }

  def parseCommonOptions(
    args: Array[String]
  ): (Option[CommonOptions], Array[String]) = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError: Option[Boolean] = Option(false)

      override def renderingMode: RenderingMode.TwoColumns.type =
        RenderingMode.TwoColumns
    }

    val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
      val log = SysLogger()
      override def displayToOut(msg: String): Unit = { log.info(msg) }

      override def displayToErr(msg: String): Unit = { log.error(msg) }

      override def reportError(msg: String): Unit = { log.error(msg) }

      override def reportWarning(msg: String): Unit = { log.warn(msg) }

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneArgs = args.map(_.trim).filter(_.nonEmpty)
    val options = saneArgs.takeWhile(_.startsWith("-"))
    val remainingOptions = saneArgs.dropWhile(_.startsWith("-"))
    val (common, effects1) = OParser.runParser[CommonOptions](
      commonOptionsParser,
      options,
      com.reactific.riddl.language.CommonOptions(),
      setup
    )
    OParser.runEffects(effects1, dontTerminate)
    common -> remainingOptions
  }
}
