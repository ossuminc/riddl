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

package com.reactific.riddl.prettify

import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.commands.{CommandOptions, TranslationCommand}
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

object PrettifyCommand {
  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] =
      Some(Path.of(System.getProperty("java.io.tmpdir"))),
    projectName: Option[String] = None,
    singleFile: Boolean = true,
    commonOptions: CommonOptions = CommonOptions()
  ) extends CommandOptions with TranslationCommand.Options {
    def command: String = "t"

  }
}

/** A command to Prettify RIDDL Source */
class PrettifyCommand extends
  TranslationCommand[PrettifyCommand.Options]("prettify") {
  import PrettifyCommand.Options

  def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def translateImpl(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: Options
  ): Either[Messages, Unit] = {
    PrettifyTranslator
      .translate(root, log, commonOptions, options)
      .map(_ => ())
  }

  override def getOptions: (OParser[Unit, Options], Options) =  {
    val builder = OParser.builder[Options]
    import builder._
    cmd(pluginName).children(
      inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
      outputDir((v, c) => c.copy(outputDir = Option(v.toPath))),
      opt[Boolean]('s', name = "single-file")
        .action((v, c) => c.copy(singleFile = v))
        .text(
          """Resolve all includes and imports and write a single file with the
            |same file name as the input placed in the out-dir"""
            .stripMargin
        )
    ).text(
      """Parse and validate the input-file and then reformat it to a
        |standard layout written to the output-dir.  """.stripMargin
    ) -> PrettifyCommand.Options()
  }
  override def getConfigReader:
  ConfigReader[Options] = { (cur: ConfigCursor) =>
    for {
      objCur <- cur.asObjectCursor
      cmdCur <- objCur.atKey(pluginName)
      content <- cmdCur.asObjectCursor
      inputPathRes <- content.atKey("input-file")
      inputPath <- inputPathRes.asString
      outputPathRes <- content.atKey("output-dir")
      outputPath <- outputPathRes.asString
      projectName <-
        optional(content, "project-name", "No Project Name Specified") {
          cur => cur.asString
        }
      singleFileRes <- objCur.atKey("single-file")
      singleFile <- singleFileRes.asBoolean
    } yield PrettifyCommand.Options(
      Option(Path.of(inputPath)),
      Option(Path.of(outputPath)),
      Option(projectName),
      singleFile
    )
  }
}
