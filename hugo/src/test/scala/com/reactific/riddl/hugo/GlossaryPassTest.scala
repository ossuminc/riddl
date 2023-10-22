package com.reactific.riddl.hugo

import com.reactific.riddl.testkit.ValidatingTest
import com.reactific.riddl.passes.Pass

class GlossaryPassTest extends ValidatingTest {

  val dir = "testkit/src/test/input/"

  "GlossaryPass" must {
    "product glossary entries" in {
      parseAndValidateTestInput("glossary entries", "everything.riddl", dir) { case (root, pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new GlossaryPass(pr.input, pr.outputs, HugoCommand.Options())
          val output: GlossaryOutput = Pass.runPass[GlossaryOutput](pr.input, pr.outputs, pass)
          output.entries.size must be(60)
      }
    }
  }
}
