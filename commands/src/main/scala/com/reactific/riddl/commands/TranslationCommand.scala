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

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.language.TranslatingOptions
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Validation.Result
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

/** An abstract base class for translation style commands. That is, they
  * translate an input file into an output directory of files.
  * @param name
  *   The name of the command to pass to [[CommandPlugin]]
  * @tparam OPT
  *   The option type for the command
  */
abstract class TranslationCommand[OPT <: TranslationCommand.Options: ClassTag](
  name: String)
    extends CommandPlugin[OPT](name) {

  /** Implement this in your subclass to do the translation. The input will have
    * been parsed and validated already so the job is to translate the root
    * argument into the directory of files.
    *
    * @param root
    *   The RootContainer providing the parsed/validated input
    * @param log
    *   A Logger to use for messages. Use sparingly, not for errors
    * @param commonOptions
    *   The options common to all commands
    * @param options
    *   The options specific to your subclass implementation
    * @return
    *   A Right[Unit] if successful or Left[Messages] if not
    */
  protected def translateImpl(
    validationResult: Validation.Result,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, Unit]

  def overrideOptions(options: OPT, newOutputDir: Path): OPT

  override final def run(
    originalOptions: OPT,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    val options =
      if (outputDirOverride.nonEmpty) {
        overrideOptions(originalOptions, outputDirOverride.get)
      } else { originalOptions }

    val msgs1 =
      if (options.inputFile.isEmpty) {
        Messages.errors("An input path was not provided.")
      } else { Messages.empty }
    val msgs2 =
      if (options.outputDir.isEmpty) {
        Messages.errors("An output path was not provided.")
      } else { Messages.empty }

    val messages = msgs1 ++ msgs2

    if (messages.nonEmpty) {
      Left[Messages, Unit](
        messages
      ) // no point even parsing if there are option errors
    } else {
      options.withInputFile { inputFile: Path =>
        Riddl.parse(inputFile, commonOptions).flatMap { root: RootContainer =>
          Riddl.validate(root, commonOptions) match {
            case result: Result =>
              if (result.messages.hasErrors) {
                if (commonOptions.debug) {
                  println("Errors after running validation:")
                  println(result.messages.format)
                }
                Left[Messages, Unit](result.messages)
              } else {
                val showTimes = commonOptions.showTimes
                Riddl.timer(stage = "translate", showTimes) {
                  translateImpl(result, log, commonOptions, options)
                }
              }
          }
        }
      }
    }
  }
}
