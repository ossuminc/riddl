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

import com.reactific.riddl.language.{CommonOptions, Messages, Riddl, TranslatingOptions}
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.reflect.ClassTag

object TranslationCommand {
  trait Options extends TranslatingOptions with CommandOptions {
    def inputFile: Option[Path]
    def outputDir: Option[Path]
    def projectName: Option[String]
  }
}

/**
 * An abstract base class for translation style commands. That is, they
 * translate an input file into an output directory of files.
 * @param name The name of the command to pass to [[CommandPlugin]]
 * @tparam OPT The option type for the command
 */
abstract class TranslationCommand[OPT <: TranslationCommand.Options : ClassTag](name: String)
  extends CommandPlugin[OPT](name) {

  /**
   * Implement this in your subclass to do the translation. The input will have
   * been parsed and validated already so the job is to translate the root
   * argument into the directory of files.
   *
   * @param root The [[RootContainer]] providing the parsed/validated input
   * @param log A [[Logger]] to use for messages. Use sparingly, not for errors
   * @param commonOptions The options common to all commands
   * @param options The options specific to your subclass implementation
   * @return A Right[Unit] if successful or Left[Messages] if not
   */
  protected def translateImpl(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, Unit]

  final def run(
    options: OPT,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    val showTimes = commonOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      options.withInputFile { inputFile: Path =>
        Riddl.parseAndValidate(inputFile, commonOptions).map { root =>
          if (commonOptions.verbose) log
            .info(s"Starting translation of `${root.id.format}")
          val messages =
            (if (options.inputFile.isEmpty) {
               Messages.errors("An input path was not provided.")
             } else { Messages.empty }) ++
              (if (options.outputDir.isEmpty) {
                 Messages.errors("An output path was not provided.")
               } else { Messages.empty })
          if (messages.nonEmpty) { Left(messages) }
          else { translateImpl(root, log, commonOptions, options) }
        }
      }
    }
  }

 /*
  def getOptions(log: Logger): (OParser[Unit, OPT], OPT) = {
    import builder.*
    cmd(name)
      .children(
        arg[File]("input-file").action((f, opt) =>
      opt.copy(inputFile = Some(f.toPath))
    )) -> InputFileCommandPlugin.Options()
  }

  override def getConfigReader(
    log: Logger
  ): ConfigReader[OPT] = { (cur: ConfigCursor) =>
    {
      for {
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(name)
        objCur <- topRes.asObjectCursor
        inFileRes <- objCur.atKey("input-file").map(_.asString)
        inFile <- inFileRes
        outDirRes <- objCur.atKey("output-dir").map(_.asString)
        outDir <- outDirRes
        projNameRes <- objCur.atKey("project-name").map(_.asString)
        projName <- projNameRes
      } yield {
        projNameRes
      }
    }
  }
  */

}
