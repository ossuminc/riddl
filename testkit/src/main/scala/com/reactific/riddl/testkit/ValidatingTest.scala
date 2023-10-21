/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.testkit.ParsingTest
import com.reactific.riddl.passes.{Pass, PassesResult, Riddl}
import org.scalatest.*

import java.io.File

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  protected def runStandardPasses(
    model: RootContainer,
    options: CommonOptions,
    shouldFailOnErrors: Boolean = false
  ): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(model, options)
    if shouldFailOnErrors && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
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
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            ao.root.inputs mustNot be(empty)
            validation(root, root.inputs.head, ao.messages)
        }
    }
  }

  def parseValidateAndThen[T](
    rpi: RiddlParserInput,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    andThen: (PassesResult, RootContainer, RiddlParserInput, Messages) => T
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
    validation: (PassesResult, RootContainer, RiddlParserInput, Messages) => Assertion
  ): Assertion = {
    parseValidateAndThen[Assertion](rpi, commonOptions, shouldFailOnErrors) {
      (passesResult: PassesResult, root: RootContainer, rpi: RiddlParserInput, messages: Messages) =>
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
    validation: (RootContainer, Messages) => Assertion = (_, msgs) => defaultFail(msgs)
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
  def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (RootContainer, Messages) => Assertion = (_, msgs) => fail(msgs.format)): Assertion = {
    val directory = "testkit/src/test/input/"
    val file = new File(directory + fileName)
    Riddl.parseAndValidate(file, options) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(result) =>
        validation(result.root, result.messages)
    }
  }

  def parseAndValidateFile(
    file: File,
    options: CommonOptions = CommonOptions()
  ): Assertion = {
    Riddl.parseAndValidate(file, options) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(result) =>
        val messages = result.messages
        val errors = messages.filter(_.kind.isError)
        errors mustBe empty
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
