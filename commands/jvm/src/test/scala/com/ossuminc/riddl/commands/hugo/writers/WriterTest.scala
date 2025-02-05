/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.commands.hugo.HugoPass
import com.ossuminc.riddl.commands.hugo.themes.GeekDocWriter
import com.ossuminc.riddl.commands.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassesResult, Riddl}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, ec, pc}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class WriterTest extends AbstractTestingBasis {

  val base = Path.of("hugo", "src", "test", "input")
  val output = Path.of("hugo", "target", "test", "adaptors")

  def makeMDW(filePath: Path, passesResult: PassesResult): MarkdownWriter = {
    GeekDocWriter(filePath, passesResult.input, passesResult.outputs, HugoPass.Options())
  }

  def validateRoot(input: RiddlParserInput)(
    validate: (root: PassesResult) => Assertion
  ) = {
    Riddl.parseAndValidate(input) match {
      case Left(messages)                    => fail(messages.justErrors.format)
      case Right(passesResult: PassesResult) => validate(passesResult)
    }
  }
}
