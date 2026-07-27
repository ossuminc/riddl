/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A51: include hygiene (validation-only subset). An include that contributes no definitions draws
  * a MissingWarning; a healthy include (with definitions and a .riddl origin) draws neither the A51
  * MissingWarning nor the A51 suffix StyleWarning.
  */
class IncludeHygieneTest extends JVMAbstractValidatingTest {

  "Include hygiene (A51)" should {

    "emit a MissingWarning when an include contributes no definitions" in { (td: TestData) =>
      validateFile("a51-empty", "a51-empty-include/main.riddl") { case (_, messages) =>
        val missing = messages.filter(m => m.message.contains("Include contributes no definitions"))
        assert(missing.nonEmpty, s"expected empty-include MissingWarning, got:\n${messages.format}")
      }
    }

    "not emit any A51 include warning for a healthy .riddl include" in { (td: TestData) =>
      validateFile("a51-good", "a51-good-include/main.riddl") { case (_, messages) =>
        messages.filter(m =>
          m.message.contains("Include contributes no definitions") ||
            m.message.contains("should end with .riddl")
        ) mustBe empty
      }
    }
  }
}
