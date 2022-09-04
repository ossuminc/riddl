package com.reactific.riddl.translator.hugo_git_check
import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.io.File
import java.nio.file.Path

object GitCheckCommand {
  case class Options(
    gitCloneDir: Option[Path] = None,
    relativeDir: Option[Path] = None,
    userName: String = "",
    accessToken: String = ""
  ) extends CommandOptions {
    def command: String = "hugo-git-check"
    def inputFile: Option[Path] = None
  }
}
/** HugoGitCheck Command */
class GitCheckCommand extends CommandPlugin[GitCheckCommand.Options](
  "hugo-git-check"
) {
  import GitCheckCommand.Options
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
          .text(
            """Provides the top directory of a git repo clone that
              |contains the <input-file> to be processed.""".stripMargin
          ),
        opt[String]("user-name")
          .optional
          .action( (n,opts) => opts.copy(userName = n))
          .text("Name of the git user for pulling from remote"),
        opt[String]("access-token")
          .optional()
          .action( (t,opts) => opts.copy(accessToken = t))
      )
      .text(
        """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the configured command is run""".stripMargin
      )
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
          GitCheckCommand.Options(
            gitCloneDir = Some(gitCloneDir.toPath),
            userName = userNameStr,
            accessToken = accessTokenStr
          )
        }
      }
  }

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
