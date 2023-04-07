package com.reactific.riddl.language.passes.resolve

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.passes.{Pass, PassInput}

/**  A base class for test cases involved in resolving pathids */
class ResolvingTest extends AnyWordSpec with Matchers {

  def resolve(
    root: RootContainer, commonOptions: CommonOptions = CommonOptions(
    showMissingWarnings = false,
    showUsageWarnings = false,
    showStyleWarnings = false
  )): PassInput = {
    val input = PassInput(root, commonOptions)
    Pass.runSymbols(input)
    Pass.runResolution(input)
    input
  }

  def parseAndResolve(
    input: RiddlParserInput
  )(
    onSuccess: PassInput => Assertion = _  => succeed
  )(onFailure: Messages => Assertion = messages => fail(messages.format)): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val input = resolve(model)
        val messages = input.getMessages
        if (messages.isEmpty)
          onSuccess(input)
        else onFailure(messages)
    }
  }
}
