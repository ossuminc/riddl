/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.{pc, CommonOptions}

import org.scalatest.TestData

/** A51: include hygiene (validation-only subset). An include that contributes no definitions draws
  * a MissingWarning; a healthy include (with definitions and a .riddl origin) draws neither the A51
  * MissingWarning nor the A51 suffix StyleWarning.
  *
  * Both cases pin their options rather than trusting the defaults. `pc` is a process-wide singleton
  * with mutable options, and a dozen sibling suites in this module flip them through
  * `pc.withOptions`; run in company rather than alone, this suite was reading whichever settings
  * some other suite had installed and seeing NO messages at all. Asking for exactly what is under
  * test makes the outcome independent of who else is running.
  */
class IncludeHygieneTest extends JVMAbstractValidatingTest {

  private val withWarnings: CommonOptions =
    CommonOptions(showWarnings = true, showMissingWarnings = true, showStyleWarnings = true)

  "Include hygiene (A51)" should {

    "emit a MissingWarning when an include contributes no definitions" in { (td: TestData) =>
      pc.withOptions(withWarnings) { _ =>
        validateFile("a51-empty", "a51-empty-include/main.riddl") { case (_, messages) =>
          val missing =
            messages.filter(m => m.message.contains("Include contributes no definitions"))
          assert(
            missing.nonEmpty,
            s"expected empty-include MissingWarning, got:\n${messages.format}"
          )
        }
      }
    }

    "not emit any A51 include warning for a healthy .riddl include" in { (td: TestData) =>
      pc.withOptions(withWarnings) { _ =>
        validateFile("a51-good", "a51-good-include/main.riddl") { case (_, messages) =>
          messages.filter(m =>
            m.message.contains("Include contributes no definitions") ||
              m.message.contains("should end with .riddl")
          ) mustBe empty
        }
      }
    }
  }
}
