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
object InfoCommand:
  case class Options(command: String = "info") extends CommandOptions:
    def inputFile: Option[Path] = None
  end Options
end InfoCommand

class InfoCommand(using pc: PlatformContext) extends Command[InfoCommand.Options]("info") {
  import InfoCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print out build information about this program") ->
      InfoCommand.Options()
  }

  override def interpretConfig(config: Config) =
    Options(commandName)
  end interpretConfig

  // Product, not diagnostic: stdout, unprefixed. See VersionCommand for the full rule.
  override def run(
    options: InfoCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    import com.ossuminc.riddl.utils.{InfoFormatter, ThirdPartyNotices}

    // Build info first -- `formatBuildInfo`, NOT `formatInfo`, because the latter already
    // appends the notices and would bury them above the JVM lines below.
    InfoFormatter.formatBuildInfo.split("\n").foreach(line => pc.stdoutln(line))

    // Add JVM-specific info (only available on JVM platform)
    pc.stdoutln(s"       jvm name: ${System.getProperty("java.vm.name")}")
    pc.stdoutln(s"    jvm version: ${System.getProperty("java.runtime.version")}")
    pc.stdoutln(s"  operating sys: ${System.getProperty("os.name")}")

    // Attribution goes LAST, after everything else.
    pc.stdoutln("")
    ThirdPartyNotices.formatted.split("\n").foreach(line => pc.stdoutln(line))
    Right(PassesResult())
  }
}
