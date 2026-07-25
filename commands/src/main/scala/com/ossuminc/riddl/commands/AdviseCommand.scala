/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext}

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** Advise Command.
  *
  * Behaves exactly like `validate` but turns on the
  * [[com.ossuminc.riddl.utils.CommonOptions.provideTips]] option, so the remediation `suggestion`
  * carried by each message is retained and shown. This is the convenient way to get AI-friendly
  * fix-it hints for every diagnostic; it replaces the former AIHelperPass-based behavior.
  *
  * Usage: riddlc advise <input.riddl> (equivalent to: riddlc --provide-tips validate <input.riddl>)
  */
class AdviseCommand(using pc: PlatformContext) extends InputFileCommand("advise") {
  import InputFileCommand.Options

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      // Force tip/suggestion generation on for the duration of validation, so
      // each message retains its remediation suggestion. The suggestions live
      // on the returned messages and are rendered by Message.format regardless
      // of the option's value at log time.
      pc.withOptions(pc.options.copy(provideTips = true)) { _ =>
        val future = RiddlParserInput.fromPath(inputFile.toString).map { rpi =>
          Riddl.parseAndValidate(rpi)
        }
        Await.result(future, 10.seconds)
      }
    }
  }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
