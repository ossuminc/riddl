package com.reactific.riddl.translator.hugo_git_check
import com.reactific.riddl.commands.{Command, CommandOptions, CommandPlugin, PluginCommand}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

object HugoGitCheckCommand {
  case class Options(
    gitCloneDir: Option[Path] = None,
    relativeDir: Option[Path] = None,
    userName: String = "",
    accessToken: String = ""
  ) extends CommandOptions {
    def command: Command = PluginCommand("hugo-git-check")
    def inputFile: Option[Path] = None
  }
}
/** HugoGitCheck Command */
class HugoGitCheckCommand extends CommandPlugin[HugoGitCheckCommand.Options](
  "hugo-git-check"
) {
  import HugoGitCheckCommand.Options
  /** Provide an scopt OParser for the commands options type, OPT
    * @param log
    *   A logger to use for output (discouraged)
    * @return
    *   A pair: the OParser and the default values for OPT
    */
  override def getOptions(
    log: Logger
  ): (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder._
    OParser.sequence(cmd("git-check")
      .children(
        opt[File]("git-clone-dir")
          .required
          .action( (f,opts) => opts.copy(gitCloneDir = Some(f.toPath)))
          .text("The root directory of a locally cloned git repository"),
        opt[String]("user-name")
          .optional
          .action( (n,opts) => opts.copy(userName = n))
          .text("Name of the git user for pulling from remote"),
        opt[String]("access-token")
          .optional()
          .action( (t,opts) => opts.copy(accessToken = t))
      )
      .text("Run a command when a git repository changes")
    ) -> Options()
  }

  implicit val hugoGitCheckReader: ConfigReader[Options] = {
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
          HugoGitCheckCommand.Options(
            gitCloneDir = Some(gitCloneDir.toPath),
            userName = userNameStr,
            accessToken = accessTokenStr
          )
        }
      }
  }

  /** Provide a typesafe/Config reader for the commands options. This reader
    * should read an object having the same name as the command. The fields of
    * that object must correspond to the fields of the OPT type.
    * @param log
    *   A logger to use for output (discouraged)
    * @return
    *   A [[pureconfig.ConfigReader[OPT]] that knows how to read [[OPT]]
    */
  override def getConfigReader(
    log: Logger
  ): ConfigReader[Options] = hugoGitCheckReader

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
    log: Logger
  ): Either[Messages, Unit] = {
    Left(errors("Not Implemented"))
  }
}
