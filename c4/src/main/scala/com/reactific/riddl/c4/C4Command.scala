/*
 * Copyright 2022 Reactific Software LLC
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

package com.reactific.riddl.c4

import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.commands.TranslationCommand
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path
import scala.annotation.unused

/** Unit Tests For HugoCommand */
object C4Command {
  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None,
    projectName: Option[String] = None,
    projectDescription: Option[String] = None,
    enterpriseName: Option[String] = None)
      extends TranslationCommand.Options {
    def command: String = "c4"
    def outputRoot: Path = outputDir.getOrElse(Path.of("")).toAbsolutePath
  }
}

class C4Command extends TranslationCommand[C4Command.Options]("c4") {
  import C4Command.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd("c4").text(
      """Convert the AST model to an equivalent C4 model using Structurizr"""
        .stripMargin
    ).children(
      inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
      outputDir((v, c) => c.copy(outputDir = Option(v.toPath))),
      opt[String]('n', "project-name").optional()
        .action((v, c) => c.copy(projectName = Option(v)))
        .text("optional project name to associate with the generated output")
        .validate(n =>
          if (n.isBlank) {
            Left("optional project-name cannot be blank or empty")
          } else { Right(()) }
        ),
      opt[String]('d', "project-description").optional()
        .action((v, c) => c.copy(projectDescription = Option(v))).text(
          "optional project description to associate with the generated output"
        ).validate(n =>
          if (n.isBlank) {
            Left("option project-description cannot be blank or empty")
          } else { Right(()) }
        ),
      opt[String]('e', "enterprise-name").optional()
        .action((v, c) => c.copy(projectDescription = Option(v)))
        .text("optional name for the enterprise as top level construct")
        .validate(n =>
          if (n.isBlank) {
            Left("option enterprise-name cannot be blank or empty")
          } else { Right(()) }
        )
    ) -> C4Command.Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for {
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inputPathRes <- objCur.atKey("input-file")
      inputPath <- inputPathRes.asString
      outputPathRes <- objCur.atKey("output-dir")
      outputPath <- outputPathRes.asString
      projectName <- optional(objCur, "project-name", "No Project Name") {
        cur => cur.asString
      }
      projectDescription <-
        optional(objCur, "project-description", "No Project Description") {
          cur => cur.asString
        }
      enterpriseName <-
        optional(objCur, "enterprise-name", "No Enterprise Name") { cur =>
          cur.asString
        }
    } yield {
      C4Command.Options(
        Option(Path.of(inputPath)),
        Option(Path.of(outputPath)),
        Option(projectName),
        Option(projectDescription),
        Option(enterpriseName)
      )
    }
  }

  def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def translateImpl(
    result: Validation.Result,
    log: Logger,
    commonOptions: CommonOptions,
    options: Options
  ): Either[Messages, Unit] = {
    C4Translator.translate(result, log, commonOptions, options)
    Right(())
  }

  override def replaceInputFile(
    opts: Options,
    @unused inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, C4Command.Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }

}
