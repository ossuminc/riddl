package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessageKind
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessages
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions
import org.scalatest.Assertion

import scala.reflect._

/** Convenience functions for tests that do validation*/
abstract class ValidatingTest extends ParsingTest {

  def parseAndValidate[D <: Container: ClassTag](input: String)(
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
    options: ValidationOptions = Validation.defaultOptions
  )(
    validation: (RootContainer, ValidationMessages) => Assertion
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

  def assertValidationMessage(
    msgs: ValidationMessages,
    expectedKind: ValidationMessageKind,
    messageSnippet: String
  ): Assertion = {
    msgs.exists(
      m => m.message.contains(messageSnippet) && m.kind == expectedKind
    ) mustBe true
  }
}
