package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.validate.NoJVMValidatingTest
import com.ossuminc.riddl.utils.SysLogger
import org.scalatest.Suite

class RunPassTestBase extends NoJVMValidatingTest {

  def runPassesWith(
    input: RiddlParserInput,
    passToRun:PassCreator,
    commonOptions: CommonOptions = CommonOptions.empty
  )
  : PassesResult = {
    TopLevelParser.parseInput(input, commonOptions, true) match {
      case Left(messages) => fail(messages.format)
      case Right(root:Root) =>
        val passesToRun = Pass.standardPasses :+ passToRun
        val passInput = PassInput(root, commonOptions)
        val result = Pass.runThesePasses(passInput, passesToRun, SysLogger())
        if result.messages.hasErrors then fail(result.messages.justErrors.format)
        result
    }
  }
}
