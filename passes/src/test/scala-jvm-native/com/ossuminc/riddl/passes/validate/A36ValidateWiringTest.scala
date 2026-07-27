/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{ec, pc, AbstractTestingBasisWithTestData, CommonOptions}
import org.scalatest.TestData

/** Verifies A36 use-case witness/trace CompletenessWarnings surface through the standard validate
  * pass path when completeness warnings are enabled, and NOT when disabled.
  */
class A36ValidateWiringTest extends AbstractTestingBasisWithTestData {

  private val src =
    """domain D is {
      |  user U is "a user"
      |  command DoIt is { ??? }
      |  context App is { ??? }
      |  context Gateway is { ??? }
      |  epic E is {
      |    user U wants to "do" so that "done"
      |    case C is {
      |      user U wants to "do" so that "done"
      |      step send command D.DoIt from context D.App to context D.Gateway
      |    }
      |  }
      |}
      |""".stripMargin

  private def validateWith(opts: CommonOptions): Messages.Messages =
    pc.withOptions(opts) { _ =>
      val rpi = RiddlParserInput(src, "A36")
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
        case Left(msgs) => msgs
        case Right(res) => res.messages
    }

  "A36 validate wiring" should {
    "surface witness CompletenessWarning when completeness is enabled" in { (_: TestData) =>
      val msgs = validateWith(CommonOptions.default.copy(showCompletenessWarnings = true))
      msgs.exists(m =>
        m.kind == Messages.CompletenessWarning && m.message.contains("is not witnessed")
      ) mustBe true
    }
    "emit no witness warning when completeness is disabled" in { (_: TestData) =>
      val msgs = validateWith(CommonOptions.default.copy(showCompletenessWarnings = false))
      msgs.exists(_.message.contains("is not witnessed")) mustBe false
    }
  }
}
