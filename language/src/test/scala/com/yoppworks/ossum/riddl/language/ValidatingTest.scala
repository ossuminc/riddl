package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Validation.{ValidationMessageKind, ValidationMessages, ValidationOptions}
import org.scalatest.Assertion

import java.io.File
import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  def parseAndValidate[D <: Container : ClassTag](
    input: String
  )(
    validation: (D, ValidationMessages) => Assertion
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

  def validateFile(
    label: String,
    fileName: String,
    options: ValidationOptions = ValidationOptions.Default
  )(validation: (RootContainer, ValidationMessages) => Assertion
  ): Assertion = {
    val directory = "language/src/test/input/"
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root, options)
        validation(root, messages)
    }
  }

  def parseAndValidateFile(
    label: String,
    file: File,
    options: ValidationOptions = ValidationOptions.Default
  ): Assertion = {
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root, options)
        val errors = messages.filter(_.kind.isError)
        val warnings = messages.iterator.filter(_.kind.isWarning)
        info(s"${errors.length} Errors:")
        info(errors.iterator.map(_.format).mkString("\n"))
        info(s"${warnings.length} Warnings:")
        info(warnings.iterator.map(_.format).mkString("\n"))
        errors mustBe empty
        warnings mustBe empty
    }
  }

  def assertValidationMessage(
    msgs: ValidationMessages,
    expectedKind: ValidationMessageKind,
    messageSnippet: String
  ): Assertion = {
    assert(
      msgs.exists(m => m.message.contains(messageSnippet) && m.kind == expectedKind),
      s"Expected but didn't find '${messageSnippet}' in:\n${msgs.mkString("\n")}"
    )
  }
}
