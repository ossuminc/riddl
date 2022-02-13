package com.yoppworks.ossum.riddl.translator.git

import com.yoppworks.ossum.riddl.language.{AST, CommonOptions, Logger,
  SysLogger, TranslatingOptions, Translator, ValidatingOptions}
import com.yoppworks.ossum.riddl.translator.hugo.{HugoTranslatingOptions,
  HugoTranslator}

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


object GitTranslatorOptions {
  val defaultMaxLoops = 1000
}

case class GitTranslatorOptions(
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
  gitCloneDir: Option[Path] = None,
) extends TranslatingOptions {
  def inputFile: Option[Path] = hugoOptions.inputFile
  def outputDir: Option[Path] = hugoOptions.outputDir
  def projectName: Option[String] = hugoOptions.projectName
}

object GitTranslator extends Translator[GitTranslatorOptions] {

  override protected def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: GitTranslatorOptions
  ): Seq[File] = {
    val gitCloneDir = options.gitCloneDir.get

      if ( gitHasCommitsToPull(gitCloneDir)) {}

      genHugo(commonOptions, ValidatingOptions(), options) // FIXME: wrong validating options
    Seq.empty[File]
  }

  def gitHasCommitsToPull(path: Path): Boolean = {
    false
  }

  def prepareOptions(options: GitTranslatorOptions): GitTranslatorOptions = {
    require(options.inputFile.nonEmpty, "Empty inputFile")
    require(options.outputDir.nonEmpty, "Empty outputDir")
    val inFile = options.inputFile.get
    val outDir = options.outputDir.get
    require(Files.isRegularFile(inFile), "input is not a file")
    require(Files.isReadable(inFile), "input is not readable")
    require(Files.isDirectory(outDir), "output is not a directory")
    require(Files.isWritable(outDir), "output is not writable")
    val htc = options.hugoOptions.copy(
      inputFile = Some(inFile),
      outputDir = Some(outDir),
      projectName = options.projectName,
      eraseOutput = true,
    )
    GitTranslatorOptions(
      hugoOptions = htc
    )
  }

  def genHugo(
    common: CommonOptions,
    validating: ValidatingOptions,
    options: GitTranslatorOptions
  ): Seq[File] = {
    val ht = HugoTranslator
    ht.parseValidateTranslate(SysLogger(), common, validating,  options.hugoOptions)
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

}
