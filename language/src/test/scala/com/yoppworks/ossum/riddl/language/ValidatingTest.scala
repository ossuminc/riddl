package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import scala.reflect._
import org.scalatest.Assertion

/** Convenience functions for tests that do validation*/
abstract class ValidatingTest extends ParsingTest {

  def parseAndValidate[D <: Container: ClassTag](input: String)(
    validation: (D, Seq[ValidationMessage]) => Assertion
  ): Assertion = {
    parseDefinition[D](RiddlParserInput(input)) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString("\n")
        fail(msg)
      case Right(model: D @unchecked) =>
        val msgs = Validation.validate(model)
        validation(model, msgs)
    }
  }

  def validateFile(label: String, fileName: String)(
    validation: (RootContainer, Seq[ValidationMessage]) => Assertion
  ): Assertion = {
    val directory = "language/src/test/input/"
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.map(_.format).mkString("\n")
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root)
        validation(root, messages)
    }
  }
}
