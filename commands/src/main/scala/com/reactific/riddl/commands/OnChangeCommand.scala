/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.time.Instant
import scala.jdk.CollectionConverters.*

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
    extends CommandPlugin[OnChangeCommand.Options](OnChangeCommand.cmdName) {
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

  implicit val onChangeReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    {
      for {
        objCur <- cur.asObjectCursor
        gitCloneDir <- optional[File](objCur, "git-clone-dir", new File(".")) {
          cc => cc.asString.map(s => new File(s))
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

  private def creds(options: OnChangeCommand.Options) =
    new UsernamePasswordCredentialsProvider(
      options.userName,
      options.accessToken
    )

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
      options.gitCloneDir.nonEmpty,
      s"Option 'gitCloneDir' must have a value."
    )
    val gitCloneDir = options.gitCloneDir.get
    require(Files.isDirectory(gitCloneDir), s"$gitCloneDir is not a directory.")
    val builder = new FileRepositoryBuilder
    val repository =
      builder.setGitDir(gitCloneDir.resolve(".git").toFile)
        .build // scan up the file system tree
    val git = new Git(repository)

    val when = getTimeStamp(gitCloneDir)
    val opts = prepareOptions(options)

    if (gitHasChanges(log, commonOptions, opts, git, when)) {
      pullCommits(log, commonOptions, opts, git)
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
      if (options.relativeDir.nonEmpty) {
        val relativeDir = options.relativeDir.get.toAbsolutePath
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
    options: OnChangeCommand.Options,
    git: Git
  ): Boolean = {
    try {
      if (commonOptions.verbose) {
        log.info("Pulling latest changes from remote")
      }
      val pullCommand = git.pull
      pullCommand.setCredentialsProvider(creds(options))
        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
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
