/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.TopLevelParser
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Validation
import org.scalatest.Assertion

import java.io.File

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

  def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (RootContainer, Messages) => Assertion =
      (_, msgs) => fail(msgs.format)
  ): Assertion = {
    val directory = "testkit/src/test/input/"
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val result = Validation.validate(root, options)
        validation(root, result.messages)
    }
  }

  def parseAndValidateFile(
    file: File,
    options: CommonOptions = CommonOptions()
  ): Assertion = {
    TopLevelParser.parse(file) match {
      case Left(errors) => fail(errors.format)
      case Right(root) =>
        val result = Validation.validate(root, options)
        val messages = result.messages
        val errors = messages.filter(_.kind.isError)
        val warnings = messages.filter(_.kind.isWarning)
        info(s"${errors.length} Errors:")
        if (errors.nonEmpty) { info(errors.format) }
        info(s"${warnings.length} Warnings:")
        if (warnings.nonEmpty) { info(warnings.format) }
        errors mustBe empty
        warnings.forall(_.message.contains("is unused")) mustBe true
    }
  }

  def assertValidationMessage(
    msgs: Messages,
    searchFor: String
  )(f: Message => Boolean
  ): Assertion = {
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
