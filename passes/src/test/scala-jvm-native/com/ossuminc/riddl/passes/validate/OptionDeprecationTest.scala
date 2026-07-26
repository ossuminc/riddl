/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** Task 12: the legacy `gateway`/`service`/`external`/`wrapper` context options are deprecated in
  * favor of the context-intention prefix (or an adaptor), and now surface as
  * [[Messages.Deprecation]] messages rather than StyleWarnings.
  */
class OptionDeprecationTest extends AbstractValidatingTest {

  private def contextWithOption(option: String): String =
    s"""domain d is {
       |  context c is { ??? } with { option $option }
       |}
       |""".stripMargin

  "Option deprecation (Task 12)" should {

    "emit exactly one Deprecation mentioning the intention prefix for `option gateway`" in {
      (td: TestData) =>
        val rpi = RiddlParserInput(contextWithOption("gateway"), td)
        Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
          case Left(errors) => fail(errors.format)
          case Right(result) =>
            val deps = result.messages.justDeprecations.filter { (m: Messages.Message) =>
              m.message.contains("'gateway'")
            }
            deps.size mustBe 1
            deps.head.message must include("context intention prefix")
        }
    }

    "emit a Deprecation mentioning an adaptor for `option wrapper`" in { (td: TestData) =>
      val rpi = RiddlParserInput(contextWithOption("wrapper"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val deps = result.messages.justDeprecations.filter { (m: Messages.Message) =>
            m.message.contains("'wrapper'")
          }
          deps.size mustBe 1
          deps.head.message must include("adaptor")
      }
    }

    "not emit a StyleWarning for a deprecated option" in { (td: TestData) =>
      val rpi = RiddlParserInput(contextWithOption("service"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages
            .filter(m =>
              m.kind == Messages.StyleWarning && m.message.contains("'service'")
            ) mustBe empty
          result.messages.justDeprecations.exists(_.message.contains("'service'")) must be(true)
      }
    }
  }
}
