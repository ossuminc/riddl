package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A base class for test cases involved in resolving pathids */
class ResolvingTest extends ParsingTest {

  def resolve(
    root: Root,
    commonOptions: CommonOptions = CommonOptions(
      showMissingWarnings = false,
      showUsageWarnings = false,
      showStyleWarnings = false
    )
  ): (PassInput, PassesOutput) = {
    val input = PassInput(root, commonOptions)
    val outputs = PassesOutput()
    Pass.runSymbols(input, outputs)
    Pass.runResolution(input, outputs)
    input -> outputs
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
