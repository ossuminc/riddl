/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, CommonOptions}

import org.scalatest.TestData

/** A48: a Domain with no author (neither referenced nor defined, nor inherited from an enclosing
  * domain) draws a MissingWarning. It is suppressible via showMissingWarnings.
  */
class NoAuthorWarningTest extends AbstractValidatingTest {

  private val noAuthor =
    """domain d is {
      |  type T is Integer
      |}
      |""".stripMargin

  "No-author warning (A48)" should {

    "emit a MissingWarning for a domain with no author" in { (td: TestData) =>
      val rpi = RiddlParserInput(noAuthor, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val authorWarnings =
          msgs.filter(m => m.kind == Messages.MissingWarning && m.message.contains("has no author"))
        authorWarnings.size mustBe 1
      }
    }

    "not warn when the domain defines an author" in { (td: TestData) =>
      val input =
        """domain d is {
          |  author Reid is { name: "Reid" email: "reid@example.com" }
          |  type T is Integer
          |}
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(_.message.contains("has no author")) mustBe empty
      }
    }

    "not warn a nested domain when an enclosing domain supplies an author" in { (td: TestData) =>
      val input =
        """domain outer is {
          |  author Reid is { name: "Reid" email: "reid@example.com" }
          |  domain inner is {
          |    type T is Integer
          |  }
          |}
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.message.contains("has no author") && m.message.contains("inner")) mustBe
          empty
      }
    }

    "suppress the warning when showMissingWarnings is false" in { (td: TestData) =>
      val rpi = RiddlParserInput(noAuthor, td)
      pc.withOptions(CommonOptions(showMissingWarnings = false)) { _ =>
        parseAndValidateInput(rpi, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          msgs.filter(_.message.contains("has no author")) mustBe empty
        }
      }
    }
  }
}
