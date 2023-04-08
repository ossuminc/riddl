/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.passes.Pass
import com.reactific.riddl.language.passes.PassesResult
import com.reactific.riddl.language.{CommonOptions, ParsingTest}
import org.scalatest.Assertion

import java.io.File
import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  def parseAndValidateAggregate(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions()
  )(
    onSuccess: PassesResult => Assertion
  ): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        Pass.apply(model, options, shouldFailOnErrors = false) match {
          case Left(messages) =>
            fail(messages.format)
          case Right(ao: PassesResult) =>
            onSuccess(ao)
        }
    }
  }

  def parseAndValidateInContext[D <: ContextDefinition: ClassTag](
    input: String,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(validator: (D, RiddlParserInput, Messages) => Assertion): Seq[Assertion] = {
    val parseString = "domain foo is { context bar is {\n " + input + "}}\n"
    val rpi = RiddlParserInput(parseString)
    parseDefinition[Domain](rpi) match {
      case Left(errors) => fail(errors.format)
      case Right((model: Domain, _)) =>
        val clazz = classTag[D].runtimeClass
        val root = RootContainer(Seq(model), Seq(rpi))
        Pass(root, options, shouldFailOnErrors) match {
          case Left(messages) =>
            fail(messages.format)
          case Right(ao) =>
            val msgs = ao.messages
            model.contexts.head.contents.filter(_.getClass == clazz).map { (d: ContextDefinition) =>
              val reducedMessages = msgs.filterNot(_.loc.line == 1)
              validator(d.asInstanceOf[D], rpi, reducedMessages)
            }
        }
    }
  }

  def parseAndValidateContext(
    input: String,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validator: (Context, RiddlParserInput, Messages) => Assertion
  ): Assertion = {
    val parseString = "domain foo is { context bar is {\n " + input + "}}\n"
    val rpi = RiddlParserInput(parseString)
    parseDefinition[Domain](rpi) match {
      case Left(errors) => fail(errors.format)
      case Right((model: Domain, _)) =>
        val root = RootContainer(Seq(model), Seq(rpi))
        Pass(root, options, shouldFailOnErrors) match {
          case Left(errors) => fail(errors.format)
          case Right(ao) =>
            val reducedMessages = ao.messages.filterNot(_.loc.line == 1)
            validator(model.contexts.head, rpi, reducedMessages)
        }
    }
  }

  def parseAndValidateDomain(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(validator: (Domain, RiddlParserInput, Messages) => Assertion): Assertion = {
    parseDefinition[Domain](input) match {
      case Left(errors) => fail(errors.format)
      case Right((model: Domain, rpi)) =>
        val root = RootContainer(Seq(model), Seq(rpi))
        Pass(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            validator(model, rpi, ao.messages)
        }
    }
  }

  def parseAndValidate(
    input: String,
    origin: String,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (RootContainer, RiddlParserInput, Messages) => Assertion
  ): Assertion = {
    TopLevelParser.parse(input, origin) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $origin:\n$msgs")
      case Right(root) =>
        Pass(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            ao.root.inputs mustNot be(empty)
            validation(root, root.inputs.head, ao.messages)
        }
    }
  }

  private def defaultFail(msgs: Messages): Assertion = {
    fail(msgs.map(_.format).mkString("\n"))
  }

  def parseAndValidateTestInput(
    label: String,
    fileName: String,
    directory: String = "language/src/test/input/",
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (RootContainer, Messages) => Assertion = (_, msgs) =>
      defaultFail(msgs)
  ): Assertion = {
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        Pass(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            validation(root, ao.messages)
        }
    }
  }

  def parseAndValidateFile(
    file: File,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  ): Assertion = {
    TopLevelParser.parse(file) match {
      case Left(errors) => fail(errors.format)
      case Right(root) =>
        Pass(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            val messages = ao.messages
            val errors = messages.filter(_.kind.isError)
            val warnings = messages.filter(_.kind.isWarning)
            info(s"${errors.length} Errors:")
            if (errors.nonEmpty) {info(errors.format)}
            info(s"${warnings.length} Warnings:")
            if (warnings.nonEmpty) {info(warnings.format)}
            errors mustBe empty
            warnings mustBe empty
        }
    }
  }

  def assertValidationMessage(
    msgs: Messages,
    searchFor: String
  )(f: Message => Boolean): Assertion = {
    assert(
      msgs.exists(f),
      s"; expecting, but didn't find '$searchFor', in:\n${msgs.mkString("\n")}"
    )
  }

  def assertValidationMessage(
    msgs: Messages,
    expectedKind: KindOfMessage,
    content: String
  ): Assertion = {
    assert(
      msgs.exists(m => m.kind == expectedKind && m.message.contains(content)),
      s"; expecting, but didn't find '$content', in:\n${msgs.format}"
    )
  }
}
