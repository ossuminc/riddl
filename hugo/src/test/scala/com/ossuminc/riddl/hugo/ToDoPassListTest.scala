package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.testkit.ValidatingTest
import com.ossuminc.riddl.passes.Pass

class ToDoPassListTest extends ValidatingTest {

  val dir = "testkit/src/test/input/"

  "ToDoListPass" must {
    "generate ToDoItems" in {
      parseAndValidateTestInput("ToDoItem", "everything.riddl", dir) { case (root, pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new ToDoListPass(pr.input, pr.outputs, HugoCommand.Options())
          val output: ToDoListOutput = Pass.runPass[ToDoListOutput](pr.input, pr.outputs, pass)
          output.collected.size must be(32)
      }
    }
  }
}
