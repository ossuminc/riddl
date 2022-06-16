package com.reactific.riddl.translator.hugo

import com.reactific.riddl.language.testkit.ValidatingTest
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion

import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

abstract class HugoTranslateExamplesBase extends ValidatingTest {

  val directory: String = "examples/src/riddl/"
  val output: String

  def makeSrcDir(path: String): Path = { Path.of(output).resolve(path) }
  val commonOptions: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  def genHugo(projectName: String, source: String): Seq[Path] = {
    val outDir = Path.of(output).resolve(source)
    val outDirFile = outDir.toFile
    if (!outDirFile.isDirectory) outDirFile.mkdirs()
    val sourcePath = Path.of(directory).resolve(source)
    val htc = HugoTranslatingOptions(
      inputFile = Some(sourcePath),
      outputDir = Some(outDir),
      eraseOutput = true,
      projectName = Some(projectName)
    )
    val ht = HugoTranslator
    ht.parseValidateTranslate(SysLogger(), commonOptions, htc)
  }

  def runHugo(source: String): Assertion = {
    import scala.sys.process._
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
    val srcDir = makeSrcDir(source)
    Files.isDirectory(srcDir)
    val cwdFile = srcDir.toFile
    val proc = Process("hugo", cwd = Option(cwdFile))
    proc.!(logger) match {
      case 0 =>
        if (hadErrorOutput) {
          fail("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  "))
        } else if (hadWarningOutput) {
          fail("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
        } else { succeed }
      case rc: Int =>
        fail(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  def checkExamples(name: String, path: String): Assertion = {
    genHugo(name, path) mustNot be(empty) // translation must have happened
    runHugo(path)
  }
}
