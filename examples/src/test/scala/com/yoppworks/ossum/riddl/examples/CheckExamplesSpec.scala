package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.language.Validation.{ValidationMessages, ValidationOptions}
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import com.yoppworks.ossum.riddl.language.{ValidatingTest, Validation}
import org.scalatest.Assertion

import java.io.File

/** Unit Tests To Check Documentation Examples */
class CheckExamplesSpec extends ValidatingTest {

  val directory = "examples/src/riddl/"
  val roots = Map(
    "Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl",
    "DokN" -> "dokn/dokn.riddl"
  )

  val errorsOnly = ValidationOptions(
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

  "Examples" should {
    for {(name, path) <- roots} {
      s"parse and validate $name" in {
        checkOne(name, path)
      }
    }
  }
}
