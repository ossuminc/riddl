/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A49: the same glossary term name defined at two scopes with different definition text is a
  * contradiction and draws a StyleWarning. An identical redefinition is fine.
  */
class TermConsistencyTest extends AbstractValidatingTest {

  "Term consistency (A49)" should {

    "warn when the same term name has different definitions at two scopes" in { (td: TestData) =>
      val input =
        """domain d is {
          |  context c is { ??? } with {
          |    term Ledger is "a UI table widget on the dashboard"
          |  }
          |} with {
          |  term Ledger is "the authoritative accounting record"
          |}
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val conflicts = msgs.filter(m =>
          m.kind == Messages.StyleWarning && m.message.contains("is defined inconsistently")
        )
        conflicts.size mustBe 1
        conflicts.head.message must include("Ledger")
      }
    }

    "not warn when the same term name has identical definitions at two scopes" in {
      (td: TestData) =>
        val input =
          """domain d is {
            |  context c is { ??? } with {
            |    term Ledger is "the authoritative accounting record"
            |  }
            |} with {
            |  term Ledger is "the authoritative accounting record"
            |}
            |""".stripMargin
        val rpi = RiddlParserInput(input, td)
        parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          msgs.filter(_.message.contains("is defined inconsistently")) mustBe empty
        }
    }
  }
}
