package com.yoppworks.ossum.riddl.translator.git

import com.yoppworks.ossum.riddl.language.{AST, CommonOptions, Logger, TranslatingOptions, Translator}

import java.io.File
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

case class GitTranslatorOptions(
  projectName: Option[String] = None,
  rate: FiniteDuration = 1.minute, // for git check on outstanding commits
) extends TranslatingOptions

object GitTranslator extends Translator[GitTranslatorOptions] {
  val defaultOptions: GitTranslatorOptions = GitTranslatorOptions()

  override protected def translateImpl(
    root: AST.RootContainer,
    inputPath: Path,
    log: Logger,
    commonOptions: CommonOptions,
    options: GitTranslatorOptions
  ): Seq[File] = {

  }

  def runHugo(source: Path, log:Logger): Boolean = {
    import scala.sys.process._
    val srcDir = source.toFile
    require(srcDir.isDirectory, "Source directory is not a directory!")
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    var hadErrorOutput: Boolean = false
    var hadWarningOutput: Boolean = false

    def fout(line: String): Unit = {
      lineBuffer.append(line);
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
