package com.reactific.riddl.translator.hugo

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.hugo.HugoCommand
import com.reactific.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

class HugoTranslatorTest extends
  RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand](
    commandName = "hugo"
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
    command: CommandPlugin[CommandOptions],
    tmpDir: Path
  ): Assertion = {
    if (commandName == "hugo") {
      command.loadOptionsFrom(configFile) match {
        case Right(options) =>
          val outDir = options.asInstanceOf[HugoCommand.Options].outputDir.get
          runHugo(outDir,tmpDir)
        case Left(errors) =>
          fail(errors.format)
      }
    } else fail("wrong command!")
  }

  def runHugo(outputDir: Path, tmpDir: Path): Assertion = {
    import scala.sys.process.*
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
    require(Files.isDirectory(outputDir))
    val cwdFile = outputDir.toFile
    val command = "hugo"
    println(s"Running hugo with cwd=$cwdFile, tmpDir=$tmpDir")
    val proc = Process(command, cwd = Option(cwdFile))
    proc.!(logger) match {
      case 0 =>
        if (hadErrorOutput) {
          fail("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  "))
        } else if (hadWarningOutput) {
          fail("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
        } else { succeed }
      case rc: Int =>
        fail(s"hugo run failed with rc=$rc:\n  " ++
          lineBuffer.mkString("\n ", "\n  ", "\n") ++
          s"tmpDir=$tmpDir\ncwd=$cwdFile\ncommand=$command\n"
        )
    }
  }
}
