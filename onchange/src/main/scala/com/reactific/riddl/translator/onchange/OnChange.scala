/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.translator.onchange

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.*
import com.reactific.riddl.utils.Logger
import org.eclipse.jgit.api.*
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

object OnChange {

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

  def runHugo(source: Path, log: Logger): Boolean = {
    import scala.sys.process._
    val srcDir = source.toFile
    require(srcDir.isDirectory, "Source directory is not a directory!")
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    var hadErrorOutput: Boolean = false
    var hadWarningOutput: Boolean = false

    def fout(line: String): Unit = {
      lineBuffer.append(line)
      if (!hadWarningOutput && line.contains("WARN")) hadWarningOutput = true
    }

    def ferr(line: String): Unit = {
      lineBuffer.append(line); hadErrorOutput = true
    }

    val logger = ProcessLogger(fout, ferr)
    val proc = Process("hugo", cwd = Option(srcDir))
    proc.!(logger) match {
      case 0 =>
        if (hadErrorOutput) {
          log.error("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  "))
          false
        } else if (hadWarningOutput) {
          log.warn("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
          true
        } else { true }
      case rc: Int =>
        log.error(
          s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  ")
        )
        false
    }
  }

}
