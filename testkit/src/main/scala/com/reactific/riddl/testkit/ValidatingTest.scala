/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.passes.Riddl
import org.scalatest.Assertion

import java.io.File

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends ParsingTest {

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
