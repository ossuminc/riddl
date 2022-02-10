package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.{CommonOptions, SysLogger, ValidatingOptions, ValidatingTest}
import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

abstract class HugoTranslateExamplesBase extends ValidatingTest {

  val directory: String = "examples/src/riddl/"
  val output: String

  def outPath(path: String): Path = { Path.of(output).resolve(new File(path).getName) }

  val showTimes: CommonOptions =  CommonOptions(showTimes = true)

  val errorsOnly: ValidatingOptions = ValidatingOptions(
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  def genHugo(projectName: String, source: String): Seq[File] = {
    val outFile = outPath(source).toFile
    if (!outFile.isDirectory) outFile.mkdirs()
    val outDir = Some(outFile.toPath)
    val sourcePath = Path.of(directory).resolve(source)
    val htc = HugoTranslatingOptions(
      eraseOutput = true,
      projectName = Some(projectName),
      outputPath = outDir,
    )
    val ht = HugoTranslator
    ht.parseValidateTranslateFile(sourcePath, SysLogger(), showTimes, errorsOnly,  htc)
  }

  def runHugo(source: Path): Assertion = {
    import scala.sys.process._
    val srcDir = source.toFile
    srcDir.isDirectory mustBe true
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
        if (hadErrorOutput) { fail("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  ")) }
        else if (hadWarningOutput) {
          fail("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
        } else { succeed }
      case rc: Int => fail(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  def checkExamples(name: String, path: String): Assertion = {
    genHugo(name, path)
    runHugo(outPath(path))
  }
}
