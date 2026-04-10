/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, PassInput}
import com.ossuminc.riddl.passes.ai.{AIHelperOutput, AIHelperPass}
import com.ossuminc.riddl.utils.{Await, PlatformContext}
import org.ekrich.config.Config
import scopt.OParser

import java.io.File
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object AdviseCommand {
  val cmdName: String = "advise"

  case class Options(
    inputFile: Option[Path] = None,
    command: String = cmdName,
    tipsOnly: Boolean = false,
    noSnippets: Boolean = false
  ) extends CommandOptions
}

/** Analyze a RIDDL model and produce AI-friendly tips
  * for improving it.
  *
  * Usage:
  *   riddlc advise <input.riddl>
  *   riddlc advise --tips-only <input.riddl>
  *   riddlc advise --no-snippets <input.riddl>
  *   riddlc --no-ansi-messages advise <input.riddl>
  */
class AdviseCommand(using pc: PlatformContext)
    extends Command[AdviseCommand.Options](
      AdviseCommand.cmdName
    ) {
  import AdviseCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(AdviseCommand.cmdName)
      .text("Analyze a RIDDL model and produce AI-friendly tips for improvement")
      .children(
        opt[Unit]('T', "tips-only")
          .optional()
          .action((_, c) => c.copy(tipsOnly = true))
          .text("Show only Tip messages, suppress errors and warnings"),
        opt[Unit]("no-snippets")
          .optional()
          .action((_, c) => c.copy(noSnippets = true))
          .text("Suppress RIDDL code snippets in tip output"),
        inputFile((v, c) => c.copy(inputFile = Some(v.toPath)))
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    val tipsOnly =
      if obj.hasPath("tips-only") then obj.getBoolean("tips-only")
      else false
    val noSnippets =
      if obj.hasPath("no-snippets") then obj.getBoolean("no-snippets")
      else false
    Options(Some(inputFile), commandName, tipsOnly, noSnippets)
  }

  override protected def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPath(
        inputFile.toString
      ).map { rpi =>
        AIHelperPass.analyzeSource(rpi) match
          case Left(parseErrors) =>
            Left(parseErrors)
          case Right(result) =>
            val aiOutput = result
              .outputOf[AIHelperOutput](AIHelperPass.name)
            aiOutput match
              case Some(output) =>
                logTips(output.messages, options)
              case None => ()
            Right(result)
      }
      Await.result(future, 10.seconds)
    }
  }

  private def logTips(
    msgs: Messages,
    options: Options
  ): Unit = {
    val toShow =
      if options.tipsOnly then msgs.justTips
      else msgs

    for msg <- toShow do
      val text =
        if options.noSnippets then
          stripSnippets(msg.format)
        else
          msg.format
      msg.kind match
        case Messages.Tip =>
          pc.log.tip(text)
        case Messages.Error | Messages.SevereError =>
          pc.log.error(text)
        case Messages.Warning =>
          pc.log.warn(text)
        case Messages.CompletenessWarning =>
          pc.log.completeness(text)
        case _ =>
          pc.log.info(text)
    end for
  }

  private def stripSnippets(text: String): String = {
    val idx = text.indexOf("\nSuggested RIDDL:")
    if idx >= 0 then text.substring(0, idx)
    else text
  }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
