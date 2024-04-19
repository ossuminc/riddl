package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.testkit.ValidatingTest

class MessagesPassTest extends ValidatingTest {

  val dir = "hugo/src/test/input/"

  "MessagesPass" must {
    "generate MessageInfo" in {
      parseAndValidateTestInput("ToDoItem", "everything.riddl", dir) { case (root, pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new MessagesPass(pr.input, pr.outputs, HugoCommand.Options())
          val output: MessageOutput = Pass.runPass[MessageOutput](pr.input, pr.outputs, pass)
          output.collected.size must be(6)
      }
    }
  }
}
