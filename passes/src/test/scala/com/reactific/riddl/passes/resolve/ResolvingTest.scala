package com.reactific.riddl.passes.resolve

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.passes.{Pass, PassInput, PassesOutput}
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**  A base class for test cases involved in resolving pathids */
class ResolvingTest extends AnyWordSpec with Matchers {

  def resolve(
    root: RootContainer, commonOptions: CommonOptions = CommonOptions(
    showMissingWarnings = false,
    showUsageWarnings = false,
    showStyleWarnings = false
  )): (PassInput, PassesOutput) = {
    val input = PassInput(root, commonOptions)
    val outputs = PassesOutput()
    Pass.runSymbols(input, outputs)
    Pass.runResolution(input, outputs)
    input -> outputs
  }

  def parseAndResolve(
    input: RiddlParserInput
  )(
    onSuccess: (PassInput, PassesOutput) => Assertion = (_, _)  => succeed
  )(onFailure: Messages => Assertion = messages => fail(messages.format)): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val (input, outputs) = resolve(model)
        val messages = outputs.getAllMessages
        if messages.isEmpty then
          onSuccess(input, outputs)
        else onFailure(messages)
    }
  }
}
