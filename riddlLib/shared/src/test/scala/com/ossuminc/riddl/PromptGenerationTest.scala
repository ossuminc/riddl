/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

class PromptGenerationTest extends AbstractValidatingTest:
  "PromptGeneration" should {
    "generate a prompt" in { (td: TestData) =>
      val rpi = RiddlParserInput("domain foo { ??? } ", td)
      parseAndThenValidate(rpi){ (pr, root, _, _) =>
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
  }
end PromptGenerationTest
