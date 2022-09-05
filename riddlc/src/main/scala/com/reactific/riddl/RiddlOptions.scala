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

package com.reactific.riddl

import com.reactific.riddl.RIDDLC.log
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.RiddlBuildInfo
import scopt.{OParser, *}
import scopt.RenderingMode.OneColumn

import java.io.File

/** Command Line Options for Riddl compiler program */

object RiddlOptions {

  def usage: String = {
    OParser.usage(commonOptionsParser, OneColumn) ++ "\n"
    OParser.usage(commandsParser, OneColumn)
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
      override def displayToOut(msg: String): Unit = {log.info(msg)}

      override def displayToErr(msg: String): Unit = {log.error(msg)}

      override def reportError(msg: String): Unit = {log.error(msg)}

      override def reportWarning(msg: String): Unit = {log.warn(msg)}

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneArgs = args.map(_.trim).filter(_.nonEmpty)
    val options = saneArgs.takeWhile(_.startsWith("-"))
    val remainingOptions = saneArgs.dropWhile(_.startsWith("-"))
    val (common, effects1) = OParser.runParser[CommonOptions](
      commonOptionsParser, options, CommonOptions(), setup
    )
    OParser.runEffects(effects1, dontTerminate)
    common -> remainingOptions
  }

  def parseCommandOptions(args: Array[String]):
  Either[Messages,CommandOptions] = {
    require(args.nonEmpty)
    val result = CommandPlugin.loadCommandNamed(args.head)
    result match {
      case Right(cmd) =>
        cmd.parseOptions(args) match {
          case Some(options) => Right(options)
          case None => Left(errors("Option parsing failed"))
        }
      case Left(messages) =>
        Left(messages)
    }
  }

  private val commandsParser: OParser[Unit, CommandOptions] = {
    val plugins = utils.Plugin
      .loadPluginsFrom[CommandPlugin[CommandOptions]]()
    val list = for {
      plugin <- plugins
    } yield {
      plugin.getOptions()
    }
    OParser.sequence(list.head._1, list.tail.map(_._1)*)
  }

  private val commonOptionsParser: OParser[Unit, CommonOptions] = {
    val builder: OParserBuilder[CommonOptions] = OParser.builder[CommonOptions]

    import builder.*

    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2022 Reactive Software LLC. All rights reserved.",
        "\nVersion: ",
        RiddlBuildInfo.version,
        "\n\nThis program parses, validates and translates RIDDL sources to other kinds",
        "\nof documents. RIDDL is a language for system specification based on Domain",
        "\nDrive Design, Reactive Architecture, and Agile principles.\n"
      ),
      opt[Unit]('t', name = "show-times").optional()
        .action((_, c) => c.copy(showTimes = true))
        .text("Show compilation phase execution times "),
      opt[Boolean]('d', "dry-run").optional()
        .action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose").optional()
        .action((_, c) => c.copy(verbose = true))
       .text("Provide verbose output detailing riddlc's actions"),
      opt[Boolean]('D', "debug").optional()
        .action((_,c) => c.copy(debug = true))
       .text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', "quiet").optional()
        .action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('w', name = "suppress-warnings").optional()
        .action((_, c) =>
          c.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false
          )
        )
        .text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings").optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings").optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Show warnings about questionable input style. "),
      opt[File]('P', name="plugins-dir").optional()
        .action((file,c) => c.copy(pluginsDir = Some(file.toPath)))
        .text("Load riddlc command extension plugins from this directory."),
    )
  }
}
