/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{PlatformContext, RiddlBuildInfo}
import org.ekrich.config.*
import scopt.OParser

import java.nio.file.Path

/** Unit Tests For FromCommand */
object VersionCommand {
  case class Options(command: String) extends CommandOptions:
    def inputFile: Option[Path] = None
  end Options
}

class VersionCommand(using pc: PlatformContext) extends Command[VersionCommand.Options]("version") {
  import VersionCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print the version of riddlc and exits") -> VersionCommand.Options(commandName)
  }

  override def interpretConfig(config: Config): Options =
    Options(commandName)
  end interpretConfig

  /** The output of this command IS its product, so it goes to STDOUT, unprefixed.
    *
    * `pc.log` writes to stderr with an `[info]` prefix, which is right for diagnostics and wrong
    * here: rc.24 moved the logger to stderr and thereby emptied this command's stdout entirely,
    * which broke sbt-riddl's version check (`riddlc version () has insufficient semantic
    * versioning parts`) and with it every build gate pinning rc.24. The rule that resolves it:
    * **diagnostics on stderr, the command's product on stdout** -- so `validate`'s messages are
    * stderr, while `version`, `info`, `about`, `help` and `stats` print their result on stdout.
    */
  override def run(
    options: VersionCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if pc.options.verbose || !pc.options.quiet then {
      pc.stdoutln(RiddlBuildInfo.version)
    }
    Right(PassesResult())
  }
}
