/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import org.scalatest.TestData

class PromptGenerationTest extends JVMAbstractValidatingTest:
  "PromptGeneration" should {
    "generate a prompt" in { (td: TestData) =>
      val rpi = RiddlParserInput("domain foo { ??? } ", td)
      parseAndThenValidate(rpi) { (pr, root, _, _) =>
        val promptGen = PromptGeneration(pr)
        val domain = root.domains.head
        promptGen(domain, "Tell me more") match
          case Left(messages) =>
            fail(messages.format)
          case Right(prompt: String) =>
            info(prompt)
            succeed
        end match
      }
    }
    "generate a prompt for a test file" in { (td: TestData) =>
      parseAndValidateTestInput("Reactive BBQ", "rbbq.riddl") { case (root, result) =>
        val promptGen = PromptGeneration(result)
        val finder = Finder(root.contents)
        val entity = finder.recursiveFindByType[Entity].last
        entity.handlers.head
        promptGen(entity, "Tell me more") match
          case Left(messages) =>
            fail(messages.format)
          case Right(prompt: String) =>
            info(prompt)
            succeed
        end match
      }
    }
  }
end PromptGenerationTest
