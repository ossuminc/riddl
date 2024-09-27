/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{NoJVMParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.passes.{Pass, PassesResult}
import org.scalatest.Assertion

import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class NoJVMValidatingTest extends NoJVMParsingTest {

  protected def runStandardPasses(
    model: Root,
    options: CommonOptions,
    shouldFailOnErrors: Boolean = false
  ): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(model, options)
    if shouldFailOnErrors && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  def simpleParseAndValidate(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions()
  ): Either[Messages, PassesResult] = {
    TopLevelParser.parseInput(input) match {
      case Left(messages) => Left(messages)
      case Right(model) =>
        runStandardPasses(model, options) match {
          case Left(messages)              => Left(messages)
          case Right(result: PassesResult) => Right(result)
        }
    }
  }

  def parseAndValidateAggregate(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions()
  )(
    onSuccess: PassesResult => Assertion
  ): Assertion = {
    TopLevelParser.parseInput(input) match {
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
    val rpi = RiddlParserInput(parseString, "test")
    parseDefinition[Domain](rpi) match {
      case Left(errors) => fail(errors.format)
      case Right((model: Domain, _)) =>
        val clazz = classTag[D].runtimeClass
        val root = Root(Contents(model))
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(messages) =>
            fail(messages.format)
          case Right(result) =>
            val msgs = result.messages
            model.contexts.head.contents
              .filter(_.getClass == clazz)
              .map { (d: ContextContents) =>
                val reducedMessages = msgs.filterNot(_.loc.line == 1)
                validator(d.asInstanceOf[D], rpi, reducedMessages)
              }
              .toSeq
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
    val rpi = RiddlParserInput(parseString, "test")
    parseDefinition[Domain](rpi) match {
      case Left(errors) => fail(errors.format)
      case Right((model: Domain, _)) =>
        val root = Root(Contents(model))
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
      case Left(messages) =>
        val errors = messages.justErrors
        if shouldFailOnErrors && errors.nonEmpty then {
          fail(errors.format)
        } else {
          val loc: At = (1, 1, input)
          validator(Domain(loc, Identifier(loc, "stand-in")), input, messages)
        }
      case Right((model: Domain, rpi)) =>
        val root = Root(Contents(model))
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
    validation: (Root, Messages) => Assertion
  ): Assertion = {
    val rpi = RiddlParserInput(input, origin)
    parseAndValidateInput(rpi, options, shouldFailOnErrors)(validation)
  }

  def parseAndValidateInput(
    rpi: RiddlParserInput,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (Root, Messages) => Assertion
  ): Assertion = {
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In ${rpi.origin}:\n$msgs")
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            if shouldFailOnErrors then fail(errors.format)
            else validation(root, errors)
          case Right(pr: PassesResult) =>
            validation(root, pr.messages)
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
    TopLevelParser.parseInput(rpi) match {
      case Left(messages) =>
        fail(messages.justErrors.format)
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(messages) =>
            fail(messages.justErrors.format)
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
        validation(passesResult, root, rpi, messages)
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
    val condition = msgs.filter(_.kind == expectedKind).exists(_.message.contains(content))
    assert(condition, s"; expecting, but didn't find:\n$content\nin:\n${msgs.format}")
  }
}
