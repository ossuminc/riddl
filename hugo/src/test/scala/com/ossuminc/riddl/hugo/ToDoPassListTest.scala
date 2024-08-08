package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.Pass
import org.scalatest.TestData

class ToDoPassListTest extends ValidatingTest {

  val dir = "hugo/src/test/input/"

  "ToDoListPass" must {
    "generate ToDoItems" in { (td: TestData) =>
      parseAndValidateTestInput("ToDoItem", "everything.riddl", dir) { case ( _ , pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new ToDoListPass(pr.input, pr.outputs, HugoPass.Options())
          val output: ToDoListOutput = Pass.runPass[ToDoListOutput](pr.input, pr.outputs, pass)
          output.collected.size must be(32)
      }
    }
  }
}
