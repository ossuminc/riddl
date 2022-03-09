package com.reactific.riddl.translator.hugo_git_check

import com.reactific.riddl.language._
import com.reactific.riddl.translator.hugo.{HugoTranslatingOptions, HugoTranslator}
import org.eclipse.jgit.api._
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

object HugoGitCheckOptions {
  val defaultMaxLoops = 1000
}

case class HugoGitCheckOptions(
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
  gitCloneDir: Option[Path] = None,
  relativeDir: Option[Path] = None,
  userName: String = "",
  accessToken: String = ""
) extends TranslatingOptions {
  def inputFile: Option[Path] = hugoOptions.inputFile
  def outputDir: Option[Path] = hugoOptions.outputDir
  def projectName: Option[String] = hugoOptions.projectName
}

object HugoGitCheckTranslator extends Translator[HugoGitCheckOptions] {

  private def creds(options: HugoGitCheckOptions) =
    new UsernamePasswordCredentialsProvider(
      options.userName,
      options.accessToken
    )

  override protected def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: HugoGitCheckOptions
  ): Seq[Path] = {
    require(options.gitCloneDir.nonEmpty, s"Option 'gitCloneDir' must have a value.")
    val gitCloneDir = options.gitCloneDir.get
    require(Files.isDirectory(gitCloneDir), s"$gitCloneDir is not a directory.")
    val builder = new FileRepositoryBuilder
    val repository = builder.setGitDir(gitCloneDir.resolve(".git").toFile)
      .build // scan up the file system tree
    val git = new Git(repository)

    val when = getTimeStamp(gitCloneDir)
    val opts = prepareOptions(options)

    if ( gitHasChanges(log, commonOptions, opts, git, when)) {
      pullCommits(log, commonOptions, options, git)
      val ht = HugoTranslator
      ht.translate(root, log, commonOptions, options.hugoOptions)
    } else {
      Seq.empty[Path]
    }
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
    options: HugoGitCheckOptions,
    git: Git,
    minTime: FileTime
  ): Boolean = {
    val repo = git.getRepository
    val top = repo.getDirectory.getParentFile.toPath.toAbsolutePath
    val subPath = if (options.relativeDir.nonEmpty) {
      val relativeDir = options.relativeDir.get.toAbsolutePath
      val relativized = top.relativize(relativeDir)
      if (relativized.getNameCount > 1) relativized.toString else "."
    } else { "."}
    val status = git.status()
      .setProgressMonitor(DotWritingProgressMonitor(log,commonOptions))
      .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
      .addPath(subPath)
      .call()

    val potentiallyChangedFiles = (
      status.getAdded.asScala ++
      status.getChanged.asScala ++ status.getModified.asScala
    ).toSet[String]

    val maybeModified = for {
      fName <- potentiallyChangedFiles
      timestamp = Files.getLastModifiedTime(Path.of(fName))
      isModified = timestamp.compareTo(minTime) > 0
    } yield {
      isModified
    }
    maybeModified.exists( x => x )
  }

  def pullCommits(
    log: Logger,
    commonOptions: CommonOptions,
    options: HugoGitCheckOptions,
    git: Git
  ) : Boolean = {
    try {
      if (commonOptions.verbose) {
        log.info("Pulling latest changes from remote")
      }
      val pullCommand = git.pull
      pullCommand
        .setCredentialsProvider(creds(options))
        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
        .setStrategy(MergeStrategy.THEIRS)
        pullCommand.call.isSuccessful
    } catch {
      case e: GitAPIException =>
        log.error("Error when pulling latest changes:", e)
        false
    }
  }

  def prepareOptions(options: HugoGitCheckOptions): HugoGitCheckOptions = {
    require(options.inputFile.nonEmpty, "Empty inputFile")
    require(options.outputDir.nonEmpty, "Empty outputDir")
    val inFile = options.inputFile.get
    val outDir = options.outputDir.get
    require(Files.isRegularFile(inFile), "input is not a file")
    require(Files.isReadable(inFile), "input is not readable")
    if (!Files.isDirectory(outDir)) {
      outDir.toFile.mkdirs()
    }
    require(Files.isDirectory(outDir), "output is not a directory")
    require(Files.isWritable(outDir), "output is not writable")
    val htc = options.hugoOptions.copy(
      inputFile = Some(inFile),
      outputDir = Some(outDir),
      projectName = options.projectName,
      eraseOutput = true,
    )
    options.copy(hugoOptions = htc)
  }


  def runHugo(source: Path, log:Logger): Boolean = {
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

    def ferr(line: String): Unit = { lineBuffer.append(line); hadErrorOutput = true }

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
        } else {
          true
        }
      case rc: Int =>
        log.error(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
        false
    }
  }

  case class DotWritingProgressMonitor(log: Logger, options: CommonOptions) extends
    ProgressMonitor {
    override def start(totalTasks: Int): Unit = {
      if (options.verbose) {
        log.info(s"Starting Fetch with $totalTasks tasks.")
      } else {
        System.out.print("\n.")
      }
    }

    override def beginTask(title: String, totalWork: Int): Unit = {
      if (options.verbose) {
        log.info(s"Starting Task '$title', $totalWork remaining.")
      } else {
        System.out.print(".")
      }
    }

    override def update(completed: Int): Unit = {
      if (options.verbose) {
        log.info(s"$completed tasks completed.")
      } else {
        System.out.print(".")
      }
    }

    override def endTask(): Unit = {
      if (options.verbose) {
        log.info(s"Task completed.")
      } else {
        System.out.println(".")
      }
    }

    override def isCancelled: Boolean = false
  }
}
