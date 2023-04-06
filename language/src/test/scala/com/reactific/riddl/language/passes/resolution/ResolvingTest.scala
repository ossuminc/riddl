package com.reactific.riddl.language.passes.resolution

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.passes.{ParserOutput, Pass}

/**  A base class for test cases involved in resolving pathids */
class ResolvingTest extends AnyWordSpec with Matchers {

  def resolve(
    root: RootContainer, commonOptions: CommonOptions = CommonOptions(
    showMissingWarnings = false,
    showUsageWarnings = false,
    showStyleWarnings = false
  )): ResolutionOutput = {
    val input = ParserOutput(root, commonOptions)
    val symbols = Pass.runSymbols(input)
    Pass.runResolution(symbols)
  }

  def parseAndResolve(
    input: RiddlParserInput
  )(
    onSuccess: ResolutionOutput => Assertion = _  => succeed
  )(onFailure: Messages => Assertion = messages => fail(messages.format)): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val resolutionOutput = resolve(model)
        val messages = resolutionOutput.symbols.messages ++ resolutionOutput.messages
        if (messages.isEmpty)
          onSuccess(resolutionOutput)
        else onFailure(messages)
    }
  }
}
