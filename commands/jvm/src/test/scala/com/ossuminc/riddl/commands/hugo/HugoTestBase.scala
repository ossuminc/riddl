/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo

import com.ossuminc.riddl.commands.hugo.{HugoOutput, HugoPass}
import com.ossuminc.riddl.commands.hugo.themes.GeekDocWriter
import com.ossuminc.riddl.commands.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, StringParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, PassesResult}
import com.ossuminc.riddl.utils.{CommonOptions, ec, pc}
import org.scalatest.Assertion

import java.nio.file.Path

abstract class HugoTestBase extends JVMAbstractValidatingTest {

  def runHugoOn(input: String): Either[Messages, (PassesResult, Root, RiddlParserInput)] = {
    val rpi = RiddlParserInput(input, "hugo Test")
    val options = HugoPass.Options(Some(Path.of(".")), Some(Path.of("target/hugo-test")))
    val passes = HugoPass.getPasses(options)

    TopLevelParser.parseString(input) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) =>
        val passInput = PassInput(root)
        val result = Pass.runThesePasses(passInput, passes)
        if result.messages.hasErrors then Left(result.messages)
        else Right((result, root, rpi))
    }
  }

  def runHugoAndAssert(input: String)(
    checker: (PassesResult, Root, RiddlParserInput) => Assertion
  ): Assertion = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult, root, rpi)) =>
        checker(passesResult, root, rpi)
    }
  }

  def makeMDW(filePath: Path, passesResult: PassesResult): MarkdownWriter = {
    GeekDocWriter(filePath, passesResult.input, passesResult.outputs, HugoPass.Options())
  }

  def makeMDWFor(input: String): (PassesResult, Root, MarkdownWriter) = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult: PassesResult, root: Root, rpi: RiddlParserInput)) =>
        val filePath = Path.of(rpi.root.path)
        passesResult.outputOf[HugoOutput](HugoPass.name) match
          case None => fail("No output from hugo pass")
          case Some(_) =>
            val mdw = makeMDW(filePath, passesResult)
            (passesResult, root, mdw)
    }
  }
}
