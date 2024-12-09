/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo

import com.ossuminc.riddl.commands.hugo.{HugoPass, ToDoListOutput, ToDoListPass}
import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.TestData

class ToDoPassListTest extends JVMAbstractValidatingTest {

  val dir = "commands/input/"

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
