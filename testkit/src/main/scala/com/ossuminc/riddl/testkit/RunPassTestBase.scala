package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInfo, PassInput, PassesResult}
import com.ossuminc.riddl.utils.SysLogger

abstract class RunPassTestBase extends ValidatingTest {

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
        Pass.runThesePasses(passInput, passesToRun, SysLogger())
    }
  }
}
