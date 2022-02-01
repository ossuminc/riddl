package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.language.Validation.{ValidationMessages, ValidationOptions}
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import com.yoppworks.ossum.riddl.language.{ValidatingTest, Validation}
import org.scalatest.Assertion

import java.io.File

/** Unit Tests To Check Documentation Examples */
class CheckExampleSpec extends ValidatingTest {

  val directory = "examples/src/riddl/"
  val roots = Map(
    "Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl",
    "DokN" -> "dokn/dokn.riddl"
  )

  def checkOne(name: String, path: String): Assertion = {
    val file = new File(directory + path)
    val options = ValidationOptions(
      showTimes = true,
      showMissingWarnings = false,
      showStyleWarnings = false
    )
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $name:$path:\n$msgs")
      case Right(ast) =>
        val messages = Validation.validate(ast, options)
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
