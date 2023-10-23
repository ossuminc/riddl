package com.reactific.riddl.hugo

import com.reactific.riddl.testkit.ValidatingTest
import com.reactific.riddl.passes.Pass

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
          output.map.size must be(1)
          output.map.head._2.size must be(63)
      }
    }
  }
}
