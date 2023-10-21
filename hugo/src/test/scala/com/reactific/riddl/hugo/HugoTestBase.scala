package com.reactific.riddl.hugo

import com.reactific.riddl.hugo.HugoTranslatorState
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.parsing.{RiddlParserInput, StringParserInput, TopLevelParser}
import com.reactific.riddl.passes.symbols.SymbolsPass
import com.reactific.riddl.passes.{Pass, PassInput, PassesResult}
import com.reactific.riddl.passes.validate.ValidatingTest
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion

class HugoTestBase extends ValidatingTest {

  def runHugoOn(input: String): Either[Messages, (PassesResult, RootContainer, RiddlParserInput)] = {
    val rpi = StringParserInput(input, "hugo Test")
    val logger = SysLogger()
    val commonOptions = CommonOptions.noMinorWarnings
    val options = HugoCommand.Options()
    val passes = HugoCommand.getPasses(logger, commonOptions, options)

    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) =>
        val passInput = PassInput(root, commonOptions)
        val result = Pass.runThesePasses(passInput, passes, logger)
        if result.messages.hasErrors then Left(result.messages)
        else Right((result, root, rpi))
    }
  }

  def runHugoAndAssert(input: String)(
    checker: (PassesResult, RootContainer, RiddlParserInput) => Assertion
  ): Assertion = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult, root, rpi)) =>
        checker(passesResult, root, rpi)
    }
  }

  def makeMDWFor(input: String): (PassesResult, RootContainer, MarkdownWriter) = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult: PassesResult, root: RootContainer, rpi: RiddlParserInput)) =>
        val filePath = rpi.root.toPath
        passesResult.outputOf[HugoOutput](HugoPass.name) match
          case None => fail("No output from hugo pass")
          case Some(output) =>
            val state = output.state
            val mdw = MarkdownWriter(filePath, state)
            (passesResult, root, mdw)
    }
  }
}
