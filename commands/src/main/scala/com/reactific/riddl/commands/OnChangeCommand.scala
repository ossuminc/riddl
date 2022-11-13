package com.reactific.riddl.commands

import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Messages.errors
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.io.File
import java.nio.file.Path
import com.reactific.riddl.language.*
import org.eclipse.jgit.api.*
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.submodule.SubmoduleWalk
import pureconfig.error.CannotParse

import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

object OnChangeCommand {
  final val cmdName: String = "onchange"
  final val defaultMaxLoops = 1024
  case class Options(
    inputFile: Option[Path] = None,
    watchDirectory: Option[Path] = None,
    targetCommand: String = ParseCommand.cmdName,
    refreshRate: FiniteDuration = 10.seconds,
    maxCycles: Int = defaultMaxLoops,
    interactive: Boolean = false)
      extends CommandOptions {
    def command: String = cmdName
  }
}

class OnChangeCommand
    extends CommandPlugin[OnChangeCommand.Options](OnChangeCommand.cmdName) {
  import OnChangeCommand.Options

  override def getOptions: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("onchange").children(
        opt[File]("git-clone-dir").required()
          .action((f, opts) => opts.copy(watchDirectory = Some(f.toPath)))
          .text("""Provides the top directory of a git repo clone that
                  |contains the <input-file> to be processed.""".stripMargin),
        arg[String]("target-command").required().action { (cmd, opt) =>
          opt.copy(targetCommand = cmd)
        }.text("The name of the command to select from the configuration file"),
        arg[FiniteDuration]("refresh-rate").optional().validate {
          case r if r.toMillis < 1000 =>
            Left("<refresh-rate> is too fast, minimum is 1 seconds")
          case r if r.toDays > 1 =>
            Left("<refresh-rate> is too slow, maximum is 1 day")
          case _ => Right(())
        }.action((r, c) => c.copy(refreshRate = r))
          .text("""Specifies the rate at which the <git-clone-dir> is checked
                  |for updates so the process to regenerate the hugo site is
                  |started""".stripMargin),
        arg[Int]("max-cycles").optional().validate {
          case x if x < 1           => Left("<max-cycles> can't be less than 1")
          case x if x > 1024 * 1024 => Left("<max-cycles> is too big")
          case _                    => Right(())
        }.action((m, c) => c.copy(maxCycles = m))
          .text("""Limit the number of check cycles that will be repeated.""")
      ).text(
        """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the configured command is run""".stripMargin
      )
    ) -> Options()
  }

  implicit val onChangeReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    {
      for {
        objCur <- cur.asObjectCursor
        watchDir <- optional[File](objCur, "git-clone-dir", new File(".")) {
          cc => cc.asString.map(s => new File(s))
        }
        targetCommand <- optional(objCur, "target-command", "")(_.asString)
        refreshRate <- optional(objCur, "refresh-rate", "10s")(_.asString)
          .flatMap { rr =>
            val dur = Duration.create(rr)
            if (dur.isFinite) { Right(dur.asInstanceOf[FiniteDuration]) }
            else {
              ConfigReader.Result.fail[FiniteDuration](CannotParse(
                s"'refresh-rate' must be a finite duration, not $rr",
                None
              ))
            }
          }
        maxCycles <- optional(objCur, "max-cycles", 100)(_.asInt)
        interactive <- optional(objCur, "interactive", true)(_.asBoolean)
      } yield {
        OnChangeCommand.Options(
          watchDirectory = Some(watchDir.toPath),
          targetCommand = targetCommand,
          refreshRate = refreshRate,
          maxCycles = maxCycles,
          interactive = interactive
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

  def runWhenGitChanges(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OnChangeCommand.Options
  )(doit: (
      AST.RootContainer,
      Logger,
      CommonOptions,
      OnChangeCommand.Options
    ) => Either[Messages, Unit]
  ): Either[Messages, Unit] = {
    require(
      options.watchDirectory.nonEmpty,
      s"Option 'watchDirectory' must have a value."
    )
    val gitCloneDir = options.watchDirectory.get
    require(Files.isDirectory(gitCloneDir), s"$gitCloneDir is not a directory.")
    val builder = new FileRepositoryBuilder
    val repository =
      builder.setGitDir(gitCloneDir.resolve(".git").toFile)
        .build // scan up the file system tree
    val git = new Git(repository)

    val when = getTimeStamp(gitCloneDir)
    val opts = prepareOptions(options)

    if (gitHasChanges(log, commonOptions, opts, git, when)) {
      pullCommits(log, commonOptions, git)
      doit(root, log, commonOptions, opts)
    } else { Right(()) }
  }

  private final val timeStampFileName: String = ".riddl-timestamp"
  def getTimeStamp(dir: Path): FileTime = {
    val filePath = dir.resolve(timeStampFileName)
    if (Files.notExists(filePath)) {
      Files.createFile(filePath)
      FileTime.from(Instant.MIN)
    } else {
      val when = Files.getLastModifiedTime(filePath)
      Files.setLastModifiedTime(filePath, FileTime.from(Instant.now()))
      when
    }
  }

  def gitHasChanges(
    log: Logger,
    commonOptions: CommonOptions,
    options: OnChangeCommand.Options,
    git: Git,
    minTime: FileTime
  ): Boolean = {
    val repo = git.getRepository
    val top = repo.getDirectory.getParentFile.toPath.toAbsolutePath
    val subPath =
      if (options.watchDirectory.nonEmpty) {
        val relativeDir = options.watchDirectory.get.toAbsolutePath
        val relativized = top.relativize(relativeDir)
        if (relativized.getNameCount > 1) relativized.toString else "."
      } else { "." }
    val status = git.status().setProgressMonitor(
      DotWritingProgressMonitor(System.out, log, commonOptions)
    ).setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
      .addPath(subPath).call()

    val potentiallyChangedFiles =
      (status.getAdded.asScala ++ status.getChanged.asScala ++
        status.getModified.asScala).toSet[String]

    val maybeModified = for {
      fName <- potentiallyChangedFiles
      timestamp = Files.getLastModifiedTime(Path.of(fName))
      isModified = timestamp.compareTo(minTime) > 0
    } yield { isModified }
    maybeModified.exists(x => x)
  }

  def pullCommits(
    log: Logger,
    commonOptions: CommonOptions,
    git: Git
  ): Boolean = {
    try {
      if (commonOptions.verbose) {
        log.info("Pulling latest changes from remote")
      }
      val pullCommand = git.pull
      pullCommand.setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
        .setStrategy(MergeStrategy.THEIRS)
      pullCommand.call.isSuccessful
    } catch {
      case e: GitAPIException =>
        log.severe("Error when pulling latest changes:", e)
        false
    }
  }

  def prepareOptions(
    options: OnChangeCommand.Options
  ): OnChangeCommand.Options = {
    require(options.inputFile.isEmpty, "inputFile not used by this command")
    options
  }

  def dirHasChangedSince(
    dir: Path,
    minTime: FileTime
  ): Boolean = {
    val potentiallyChangedFiles = dir.toFile.listFiles().map(_.toPath)
    val maybeModified = for {
      fName <- potentiallyChangedFiles
      timestamp = Files.getLastModifiedTime(fName)
      isModified = timestamp.compareTo(minTime) > 0
    } yield { isModified }
    maybeModified.exists(x => x)
  }

}
