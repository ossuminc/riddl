package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.passes.Pass.PassCreator
import com.ossuminc.riddl.passes.{Pass, PassInfo, PassesResult, PassInput}
import com.ossuminc.riddl.utils.SysLogger

abstract class RunPassTestBase extends ValidatingTest {

  def runPassesWith(input: PassInput, passToRun:PassCreator): PassesResult = {
    val passesToRun = Pass.standardPasses :+ passToRun
    Pass.runThesePasses(input, passesToRun, SysLogger())
  }
}
