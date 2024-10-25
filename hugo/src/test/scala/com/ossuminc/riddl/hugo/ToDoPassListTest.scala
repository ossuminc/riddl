package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.utils.{pc,ec}

import org.scalatest.TestData

class ToDoPassListTest extends JVMAbstractValidatingTest {

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
          output.collected.size must be(35)
      }
    }
  }
}
