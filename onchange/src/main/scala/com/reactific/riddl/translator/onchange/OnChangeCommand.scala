/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.translator.onchange

import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Messages.errors
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.io.File
import java.nio.file.Path

object OnChangeCommand {
  final val cmdName: String = "onchange"
  case class Options(
    gitCloneDir: Option[Path] = None,
    relativeDir: Option[Path] = None,
    userName: String = "",
    accessToken: String = "")
      extends CommandOptions {
    def command: String = cmdName
    def inputFile: Option[Path] = None
  }
}

/** HugoGitCheck Command */
class OnChangeCommand
    extends CommandPlugin[OnChangeCommand.Options](OnChangeCommand.cmdName)
 {
  import OnChangeCommand.Options

  override def getOptions: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("onchange").children(
        opt[File]("git-clone-dir").required()
          .action((f, opts) => opts.copy(gitCloneDir = Some(f.toPath)))
          .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
        opt[String]("user-name").optional()
          .action((n, opts) => opts.copy(userName = n))
          .text("Name of the git user for pulling from remote"),
        opt[String]("access-token").optional()
          .action((t, opts) => opts.copy(accessToken = t))
      ).text(
        """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the configured command is run""".stripMargin
      )
    ) -> Options()
  }

  implicit val onChangeReader: ConfigReader[Options] = {
    (cur: ConfigCursor) =>
      {
        for {
          objCur <- cur.asObjectCursor
          gitCloneDir <-
            optional[File](objCur, "git-clone-dir", new File(".")) { cc =>
              cc.asString.map(s => new File(s))
            }
          userNameRes <- objCur.atKey("user-name")
          userNameStr <- userNameRes.asString
          accessTokenRes <- objCur.atKey("access-token")
          accessTokenStr <- accessTokenRes.asString
        } yield {
          OnChangeCommand.Options(
            gitCloneDir = Some(gitCloneDir.toPath),
            userName = userNameStr,
            accessToken = accessTokenStr
          )
        }
      }
  }

  override def getConfigReader: ConfigReader[Options] = onChangeReader

  /** Execute the command given the options. Error should be returned as
    * Left(messages) and not directly logged. The log is for verbose or debug
    * output
    * @param options
    *   The command specific options
    * @param commonOptions
    *   The options common to all commands
    * @param log
    *   A logger for logging errors, warnings, and info
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = { Left(errors("Not Implemented")) }
}
