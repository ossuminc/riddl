/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{AbstractParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}
import org.scalatest.*

/** A base class for test cases involved in resolving [[com.ossuminc.riddl.language.AST.PathIdentifier]]s */
abstract class SharedResolvingTest(using pc: PlatformContext) extends AbstractParsingTest {

  def resolve(
    root: Root
  ): (PassInput, PassesOutput) = {
    pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
      val input = PassInput(root)
      val outputs = PassesOutput()
      Pass.runSymbols(input, outputs)
      Pass.runResolution(input, outputs)
      input -> outputs
    }
  }

  def parseAndResolve(
    input: RiddlParserInput
  )(
    onSuccess: (PassInput, PassesOutput) => Assertion = (_, _) => succeed
  )(onFailure: Messages => Assertion = messages => fail(messages.format)): Assertion = {
    TopLevelParser.parseInput(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val (input, outputs) = resolve(model)
        val messages = outputs.getAllMessages
        if messages.isEmpty then onSuccess(input, outputs)
        else onFailure(messages)
    }
  }
}
