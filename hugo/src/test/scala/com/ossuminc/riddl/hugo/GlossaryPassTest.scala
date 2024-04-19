package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.testkit.ValidatingTest
import com.ossuminc.riddl.passes.Pass

class GlossaryPassTest extends ValidatingTest {

  val dir = "hugo/src/test/input/"

  "GlossaryPass" must {
    "product glossary entries" in {
      parseAndValidateTestInput("glossary entries", "everything.riddl", dir) { case (root, pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new GlossaryPass(pr.input, pr.outputs, HugoCommand.Options())
          val output: GlossaryOutput = Pass.runPass[GlossaryOutput](pr.input, pr.outputs, pass)
          output.entries.size must be(58) // TODO: Enhance this test to check entry contents
      }
    }
  }
}
