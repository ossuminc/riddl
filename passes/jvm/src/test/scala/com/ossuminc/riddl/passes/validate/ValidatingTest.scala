/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{NoJVMParsingTest, ParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.passes.{Pass, PassesResult}
import org.scalatest.Assertion

import java.nio.file.Path
import scala.reflect.*

/** Convenience functions for tests that do validation */
abstract class ValidatingTest extends NoJVMValidatingTest {

  private def defaultFail(pr: PassesResult): Assertion = {
    fail(pr.messages.map(_.format).mkString("\n"))
  }
  
  def parseAndValidateTestInput(
    label: String,
    fileName: String,
    directory: String = "passes/jvm/src/test/input/",
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  )(
    validation: (Root, PassesResult) => Assertion = (_, msgs) => defaultFail(msgs)
  ): Assertion = {
    val rpi = RiddlParserInput.fromCwdPath(Path.of(directory + fileName))
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        runStandardPasses(root, options, shouldFailOnErrors) match {
          case Left(errors) =>
            fail(errors.justErrors.format)
          case Right(pr) =>
            validation(root, pr)
        }
    }
  }

  def parseAndValidateFile(
    file: Path,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  ): Assertion = {
    val rpi = RiddlParserInput.fromCwdPath(file)
    TopLevelParser.parseInput(rpi) match {
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
            errors.mustBe(empty)
            warnings.mustBe(empty)
        }
    }
  }
  
  def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (Root, Messages) => Assertion = (_, msgs) => fail(msgs.format)): Assertion = {
    val directory = "passes/jvm/src/test/input/"
    val rpi = RiddlParserInput.fromCwdPath(Path.of(directory + fileName))
    simpleParseAndValidate(rpi, options) match {
      case Left(errors: Messages) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(result) =>
        validation(result.root, result.messages)
    }
  }
}
