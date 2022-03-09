package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Validation.{ValidationMessage, ValidationMessageKind, ValidationMessages}
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import org.scalatest.Assertion

import java.io.File
import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  def parseAndValidateInContext[D <: ContextDefinition: ClassTag](
    input: String
  )(validator: (D, ValidationMessages) => Assertion
  ): Seq[Assertion] = {
    val parseString = "domain foo is { context bar is {\n " + input + "}}\n"
    parseDefinition[Domain](RiddlParserInput(parseString)) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString("\n")
        fail(msg)
      case Right(model: Domain) =>
        val msgs = Validation.validate(model)
        val clazz = classTag[D].runtimeClass
        model.contexts.head.contents.filter(_.getClass == clazz).map { d: ContextDefinition =>
          val reducedMessages = msgs.filterNot(_.loc.line == 1)
          validator(d.asInstanceOf[D], reducedMessages)
        }
    }
  }

  def parseAndValidateContext(
    input: String,
    options: CommonOptions = CommonOptions()
  )(validator: (Context, ValidationMessages) => Assertion
  ): Assertion = {
    val parseString = "domain foo is { context bar is {\n " + input + "}}\n"
    parseDefinition[Domain](RiddlParserInput(parseString)) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString("\n")
        fail(msg)
      case Right(model: Domain) =>
        val msgs = Validation.validate(model, options)
        val reducedMessages = msgs.filterNot(_.loc.line == 1)
        validator(model.contexts.head, reducedMessages)
    }
  }

  def parseAndValidate[D <: ParentDefOf[Definition]: ClassTag](
    input: String
  )(validator: (D, ValidationMessages) => Assertion
  ): Assertion = {
    parseDefinition[D](RiddlParserInput(input)) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString("\n")
        fail(msg)
      case Right(model: D @unchecked) =>
        val msgs = Validation.validate(model)
        validator(model, msgs)
    }
  }

  def parseAndValidate(
    input: String,
    testCaseName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (RootContainer, ValidationMessages) => Assertion
  ): Assertion = {
    TopLevelParser.parse(input, testCaseName) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $testCaseName:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root, options)
        validation(root, messages)
    }
  }

  def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
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
    options: CommonOptions = CommonOptions()
  ): Assertion = {
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root, options)
        val errors = messages.filter(_.kind.isError)
        val warnings: Seq[ValidationMessage] = messages.filter(_.kind.isWarning)
        info(s"${errors.length} Errors:")
        if (errors.nonEmpty) { info(errors.map(_.format).mkString("\n")) }
        info(s"${warnings.length} Warnings:")
        if (warnings.nonEmpty) {
          val asString = warnings.map(_.format).mkString("\n")
          info(asString)
        }
        errors mustBe empty
        warnings mustBe empty
    }
  }

  def assertValidationMessage(
    msgs: ValidationMessages,
    expectedKind: ValidationMessageKind,
    content: String
  ): Assertion = {
    assert(
      msgs.exists(m => m.kind == expectedKind && m.message.contains(content)),
      s"; expecting, but didn't find '$content', in:\n${msgs.mkString("\n")}"
    )
  }
}
