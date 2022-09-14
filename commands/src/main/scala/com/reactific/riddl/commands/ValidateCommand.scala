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

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.{CommonOptions, Riddl}
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.annotation.unused

/** Validate Command */
class ValidateCommand extends InputFileCommandPlugin("validate") {
  import InputFileCommandPlugin.Options
  override def run(
    options: Options,
    @unused commonOptions: CommonOptions,
    log:Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    options.withInputFile { inputFile: Path =>
      Riddl.parseAndValidate(inputFile, commonOptions).map(_ => ())
    }
  }

  override def replaceInputFile(
    opts: Options, inputFile: Path
  ): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def loadOptionsFrom(configFile: Path, commonOptions: CommonOptions):
  Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}
