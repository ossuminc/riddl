/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.passes.{Pass, PassesResult}
import org.scalatest.Assertion

import java.io.File
import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  protected def runStandardPasses(
                                   model: Root,
                                   options: CommonOptions,
                                   shouldFailOnErrors: Boolean = false
  ): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(model, options)
    if shouldFailOnErrors && result.messages.hasErrors then
      Left(result.messages)
    else
      Right(result)
  }

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
        runStandardPasses(model, options) match {
          case Left(messages) =>
            fail(messages.format)
          case Right(result: PassesResult) =>
            onSuccess(result)
        }
    }
  }

  def parseAndValidateInContext[D <: OccursInContext: ClassTag](
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
        val root = Root(Seq(model), Seq(rpi))
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(messages) =>
            fail(messages.format)
          case Right(result) =>
            val msgs = result.messages
            model.contexts.head.contents.filter(_.getClass == clazz).map { (d: OccursInContext) =>
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
        val root = Root(Seq(model), Seq(rpi))
        runStandardPasses(root, options, shouldFailOnErrors) match {
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
      case Left(errors) =>
        if shouldFailOnErrors then {
          fail(errors.format)
        } else {
          val loc: At = (1, 1, input)
          validator(Domain(loc, Identifier(loc, "stand-in")), input, errors)
        }
      case Right((model: Domain, rpi)) =>
        val root = RootContainer(List(),Seq(model), List(), Seq(rpi))
        runStandardPasses(root, options, shouldFailOnErrors) match {
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
    validation: (Root, RiddlParserInput, Messages) => Assertion
  ): Assertion = {
    TopLevelParser.parse(input, origin) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $origin:\n$msgs")
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            if shouldFailOnErrors then fail(errors.format)
            else validation(root, root.inputs.head, errors)
          case Right(pr: PassesResult) =>
            pr.root.inputs mustNot be(empty)
            validation(root, root.inputs.head, pr.messages)
        }
    }
  }

  def parseValidateAndThen[T](
    rpi: RiddlParserInput,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    andThen: (PassesResult, Root, RiddlParserInput, Messages) => T
  ): T = {
    TopLevelParser.parse(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(passesResult: PassesResult) =>
            andThen(passesResult, root, rpi, passesResult.messages)
        }
    }
  }

  def parseAndThenValidate(
    rpi: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (PassesResult, Root, RiddlParserInput, Messages) => Assertion
  ): Assertion = {
    parseValidateAndThen[Assertion](rpi, commonOptions, shouldFailOnErrors) {
      (passesResult: PassesResult, root: Root, rpi: RiddlParserInput, messages: Messages) =>
        passesResult.root.inputs mustNot be(empty)
        validation(passesResult, root, rpi, messages)
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
    validation: (Root, Messages) => Assertion = (_, msgs) => defaultFail(msgs)
  ): Assertion = {
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
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
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            val messages = ao.messages
            val errors = messages.filter(_.kind.isError)
            val warnings = messages.filter(_.kind.isWarning)
            // info(s"${errors.length} Errors:")
            // info(errors.format) }
            // info(s"${warnings.length} Warnings:")
            // info(warnings.format) }
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
    val condition = msgs.exists(m => m.kind == expectedKind && m.message.contains(content))
    assert(condition, s"; expecting, but didn't find '$content', in:\n${msgs.format}")
  }
}
