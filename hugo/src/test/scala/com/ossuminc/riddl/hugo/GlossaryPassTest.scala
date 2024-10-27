/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.TestData

class GlossaryPassTest extends JVMAbstractValidatingTest {

  val dir = "hugo/src/test/input/"

  "GlossaryPass" must {
    "product glossary entries" in { (_: TestData) =>
      parseAndValidateTestInput("glossary entries", "everything.riddl", dir) { case (root, pr: PassesResult) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors
          fail(errors.format)
        else
          val pass = new GlossaryPass(pr.input, pr.outputs, HugoPass.Options())
          val output: GlossaryOutput = Pass.runPass[GlossaryOutput](pr.input, pr.outputs, pass)
          output.entries.size must be(60) // TODO: Enhance this test to check entry contents
      }
    }
  }

  "GlossaryEntry" must {
    "construct without links" in { (_: TestData) =>
      val ge = GlossaryEntry("foo", "Context", "Just a testing sample", Seq.empty)
      ge.link must be("")
      ge.sourceLink must be("")
    }
  }
}
