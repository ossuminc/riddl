/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.{pc, ec}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{Logger, PlatformIOContext}
import scopt.OParser
import pureconfig.{ConfigCursor, ConfigReader}

import java.io.{File, PrintStream}
import java.nio.charset.Charset
import java.nio.file.Path
import com.ossuminc.riddl.command.{PassCommand, PassCommandOptions}
import com.ossuminc.riddl.passes.stats.{KindStats, StatsOutput, StatsPass}

object StatsCommand {
  val cmdName: String = "stats"
  case class Options(
    inputFile: Option[Path] = None
  ) extends PassCommandOptions
      with PassOptions {
    def command: String = cmdName
    override def check: Messages = {
      val result = super.check
      result.dropWhile(_.message.contains("output directory"))
    }
    def outputDir: Option[Path] = None
  }
}

/** Stats Command */
class StatsCommand(using io: PlatformIOContext) extends PassCommand[StatsCommand.Options]("stats") {
  import StatsCommand.Options

  // Members declared in com.ossuminc.riddl.commands.CommandPlugin
  def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inFileRes <- objCur.atKey("input-file").map(_.asString)
      inFile <- inFileRes
    yield {
      Options(inputFile = Some(Path.of(inFile)))
    }
  }

  def getOptionsParser: (OParser[Unit, Options], StatsCommand.Options) = {
    import builder.*
    cmd(StatsCommand.cmdName)
      .children(
        opt[File]('I', "input-file")
          .action { (file, opt) =>
            opt.copy(inputFile = Some(file.toPath))
          }
          .text("The main input file on which to generate statistics.")
      )
      .text("Loads a configuration file and executes the command in it") ->
      StatsCommand.Options()
  }

  // Members declared in com.ossuminc.riddl.commands.PassCommand
  def overrideOptions(options: Options, newOutputDir: Path): Options = { options }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }

  override def getPasses(options: Options): PassCreators = {
    standardPasses :+ StatsPass.creator(options)
  }

  private def logStats(stats: StatsOutput): Unit = {
    val totalStats: KindStats = stats.categories.getOrElse("All", KindStats())
    val s: String = "       Category Count Empty % Of All % Documented Completeness Complexity Containment"
    io.log.info(s)
    for {
      key <- stats.categories.keys.toSeq.sorted
      v <- stats.categories.get(key)
    } do {
      val p_of_all = v.percent_of_all(totalStats.count)
      io.log.info(
        f"$key%15s ${v.count}%5d ${v.numEmpty}%5d $p_of_all%8.2f ${v.percent_documented}12.2f ${v.completeness}%12.2f ${v.complexity}%10.2f ${v.averageContainment}%11.2f\n"
      )
    }
  }
  override def run(
    originalOptions: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val result = super.run(originalOptions, outputDirOverride)
    result match
      case Left(messages) =>
        Messages.logMessages(messages)
      case Right(passesResult) =>
        passesResult.outputOf[StatsOutput](StatsPass.name) match
          case Some(stats) => logStats(stats)
          case None        => io.log.warn("Statistics not available.")
    result
  }

}
