package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.RIDDLC
import com.yoppworks.ossum.riddl.language.Validation.{ValidationMessages, ValidationOptions}
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import com.yoppworks.ossum.riddl.language.{ValidatingTest, Validation}
import org.scalatest.Assertion

import java.io.File
import scala.collection.mutable.ArrayBuffer

/** Unit Tests To Check Documentation Examples */
class CheckExamplesSpec extends ValidatingTest {

  val directory = "examples/src/riddl/"
  val output = "examples/target/translator/"
  val roots = Map(
    "Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl",
    "DokN" -> "dokn/dokn.riddl"
  )

  val errorsOnly: ValidationOptions = ValidationOptions(
    showTimes = true,
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
        val messages = Validation.validate(ast, errorsOnly)
        val errors = messages.filter(_.kind.isError)
        val warnings: ValidationMessages = messages.filter(_.kind.isWarning)
        if (warnings.nonEmpty) {
          info(warnings.map(_.format).mkString("\n"))
        }
        if (errors.nonEmpty) {
          fail(errors.map(_.format).mkString("\n"))
        } else {
          succeed
        }
    }
  }

  def genHugo(projectName: String, source: String): Assertion = {
    val outDir = output + new File(source).getName
    val args: Array[String] = Array(
      "translate",
      "--suppress-warnings",
      "-i", source,
      "-o", outDir,
      "-p", projectName,
      "Hugo"
    )
    RIDDLC.runMain(args) mustBe true
  }

  def runHugo(source: String): Assertion = {
    import scala.sys.process._
    val srcDir = new File(source)
    srcDir.isDirectory mustBe true
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    val logger = ProcessLogger{ line => lineBuffer.append(line) }
    val proc = Process("hugo", cwd=Option(srcDir))
    proc.!(logger) match {
      case 0 =>
        succeed
      case _ =>
        fail("hugo run failed with:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  "Examples" should {
    for {(name, path) <- roots} {
      s"parse, validate, and generate $name" in {
        checkOne(name, path)
        genHugo(name, directory + path)
        val outDir = output + new File(path).getName
        runHugo(outDir)
      }
    }
  }
}
