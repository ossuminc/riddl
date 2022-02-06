package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import com.yoppworks.ossum.riddl.language.{ParsingOptions, Riddl, ValidatingTest}
import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

abstract class HugoTranslateExamplesBase extends ValidatingTest {

  val directory: String = "examples/src/riddl/"
  val output: String
  val roots: Map[String, String]

  def outPath(path: String): Path = {
    Path.of(output).resolve(new File(path).getName)
  }

  val errorsOnly: ValidatingOptions = ValidatingOptions(
    parsingOptions = ParsingOptions(showTimes = true),
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  def checkOne(name: String, path: String): Assertion = {
    val file = new File(directory + path)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $name:$path:\n$msgs")
      case Right(ast) =>
        Riddl.validate(ast, errorsOnly) mustNot be(empty)
    }
  }

  def genHugo(projectName: String, source: String): Seq[File] = {
    val outFile = outPath(source).toFile
    if (!outFile.isDirectory) outFile.mkdirs()
    val outDir = Some(outFile.toPath)
    val sourcePath = Path.of(directory).resolve(source)
    val htc = HugoTranslatingOptions(
      validatingOptions = errorsOnly,
      inputPath = Some(sourcePath),
      outputPath = outDir,
      projectName = Some(projectName)
    )
    val ht = HugoTranslator
    ht.parseValidateTranslateFile(sourcePath, htc)
  }

  def runHugo(source: Path): Assertion = {
    import scala.sys.process._
    val srcDir = source.toFile
    srcDir.isDirectory mustBe true
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    val logger = ProcessLogger { line => lineBuffer.append(line) }
    val proc = Process("hugo", cwd = Option(srcDir))
    proc.!(logger) match {
      case 0 =>
        succeed
      case _ =>
        fail("hugo run failed with:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  def runTest(name: String, path: String): Assertion = {
    checkOne(name, path)
    genHugo(name, path)
    runHugo(outPath(path))
  }
}
