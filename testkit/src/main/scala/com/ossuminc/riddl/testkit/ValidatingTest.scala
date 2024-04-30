/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult, Riddl}
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends AnyWordSpec with Matchers with ParsingTest {

  protected def runStandardPasses(
    model: Root,
    options: CommonOptions,
    shouldFailOnErrors: Boolean = false
  ): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(model, options)
    if shouldFailOnErrors && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  def parseAndValidate(
    input: String,
    origin: String,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (Root, Messages) => Assertion
  ): Assertion = {
    TopLevelParser.parseString(input) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $origin:\n$msgs")
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.format)
          case Right(ao) =>
            validation(root, ao.messages)
        }
    }
  }

  private def defaultFail(passesResult: PassesResult): Assertion = {
    fail(passesResult.messages.format)
  }

  def parseAndValidateTestInput(
    label: String,
    fileName: String,
    directory: String = "language/src/test/input/",
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (Root, PassesResult) => Assertion = (_, pr) => defaultFail(pr)
  ): Assertion = {
    val file = new File(directory + fileName)
    parseRoot(file) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val input = PassInput(root, options)
        val passesResult = Pass.runStandardPasses(input)
        validation(root, passesResult)
    }
  }
  def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (Root, Messages) => Assertion = (_, msgs) => fail(msgs.format)): Assertion = {
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
    Riddl.parseAndValidate(file, options, shouldFailOnError = false) match {
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
