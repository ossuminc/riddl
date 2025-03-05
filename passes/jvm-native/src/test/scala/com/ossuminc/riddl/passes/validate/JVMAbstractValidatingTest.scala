/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.PassRoot
import com.ossuminc.riddl.utils.{ec, pc}
import com.ossuminc.riddl.utils.{Await, PathUtils, URL}
import org.scalatest.Assertion

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class JVMAbstractValidatingTest extends AbstractValidatingTest {

  val passesTestCase = "passes/input/"

  def parseAndValidateTestInput(
    label: String,
    fileName: String,
    directory: String = passesTestCase,
    shouldFailOnErrors: Boolean = true
  )(
    validation: (Root, PassesResult) => Assertion = (_, msgs) => defaultFail(msgs)
  ): Assertion = {
    val url = URL.fromCwdPath(directory + fileName)
    val future = RiddlParserInput.fromURL(url).map { rpi =>
      TopLevelParser.parseInput(rpi) match
        case Left(errors) =>
          val msgs = errors.justErrors.format
          fail(s"In $label:\n$msgs")
        case Right(root) =>
          runStandardPasses(root, shouldFailOnErrors) match {
            case Left(errors) =>
              fail(s"In $label:\n${errors.justErrors.format}")
            case Right(pr) =>
              validation(root, pr)
          }
      end match
    }
    Await.result(future, 10.seconds)
  }

  def parseAndValidateFile(
    path: Path,
    shouldFailOnErrors: Boolean = true
  ): Assertion = {
    val url = PathUtils.urlFromCwdPath(path)
    val future = RiddlParserInput.fromURL(url).map { rpi =>
      TopLevelParser.parseInput(rpi) match {
        case Left(errors) => fail(errors.format)
        case Right(root) =>
          runStandardPasses(root, shouldFailOnErrors) match {
            case Left(errors) =>
              fail(errors.format)
            case Right(ao) =>
              val messages = ao.messages
              val errors = messages.filter(_.kind.isError)
              val warnings = messages.filter(_.kind.isWarning)
              // info(s"${errors.length} Errors:")
              // info(errors.format)
              // info(s"${warnings.length} Warnings:")
              // info(warnings.format)
              errors.mustBe(empty)
              warnings.mustBe(empty)
          }
      }
    }
    Await.result(future, 10.seconds)
  }

  def validateFile(
    label: String,
    fileName: String
  )(
    validation: (PassesResult, Messages) => Assertion = (_, msgs) => fail(msgs.format)
  ): Assertion = {
    val url = URL.fromCwdPath(passesTestCase + fileName)
    val future = RiddlParserInput.fromURL(url).map { rpi =>
      simpleParseAndValidate(rpi) match {
        case Left(errors: Messages) =>
          val msgs = errors.format
          fail(s"In $label:\n$msgs")
        case Right(result) =>
          validation(result, result.messages)
      }
    }
    Await.result(future, 10.seconds)
  }

  private def defaultFail(pr: PassesResult): Assertion = {
    fail(pr.messages.map(_.format).mkString("\n"))
  }
}
