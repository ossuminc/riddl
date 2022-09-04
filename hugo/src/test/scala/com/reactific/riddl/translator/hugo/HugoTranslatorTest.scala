package com.reactific.riddl.translator.hugo

import com.reactific.riddl.hugo.HugoCommand
import com.reactific.riddl.language.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

class HugoTranslatorTest extends
  RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand](
    commandName = "hugo",
    Path.of("hugo-translator/target/translator/")
  ) {

  "HugoTranslator" should {
    "handle all example sources" in {
      runTests()
    }
  }

  override def onSuccess(
    commandName: String,
    name: String,
    configFile: Path,
    outDir: Path
  ): Assertion = {
    if (commandName == "hugo")
      runHugo()
    else succeed
  }

  def runHugo(): Assertion = {
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
    val srcDir = outputDir
    require(Files.isDirectory(srcDir))
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
}
