/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.TestData

/** A19↔A22 conformance: a command/query type's declarative `yields` clause and the `yield`
  * statement that produces the response must agree. A command/query that declares `yields M` must
  * be handled by a clause that yields exactly `M`; a `yield` with no matching contract is an error.
  */
class YieldConformanceTest extends AbstractValidatingTest {

  private def errors(msgs: Messages.Messages): Messages.Messages = msgs.filter(_.isError)

  private def model(handlerBody: String, yieldsClause: String = "yields event E"): String =
    s"""domain D is {
       |  context C is {
       |    event E is { data: String }
       |    event Other is { data: String }
       |    command Cmd $yieldsClause is { data: String }
       |    handler H is {
       |      on command D.C.Cmd { $handlerBody }
       |    }
       |  }
       |}
       |""".stripMargin

  "yield conformance" should {

    "accept a yield that matches the declared yields clause" in { (td: TestData) =>
      val input = RiddlParserInput(model("yield event E"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val conformanceErrors = errors(msgs).filter(m =>
          m.message.contains("yield") || m.message.contains("yields")
        )
        if conformanceErrors.nonEmpty then
          info(s"Unexpected conformance errors:\n${conformanceErrors.map(_.format).mkString("\n")}")
        conformanceErrors mustBe empty
      }
    }

    "reject a yield whose message does not match the declared yields clause" in { (td: TestData) =>
      val input = RiddlParserInput(model("yield event Other"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        errors(msgs).exists(_.message.contains("does not match declared")) mustBe true
      }
    }

    "reject a handler that never yields for a yields-declaring command" in { (td: TestData) =>
      val input = RiddlParserInput(model("prompt \"do nothing\""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        errors(msgs).exists(_.message.contains("never yields")) mustBe true
      }
    }

    "reject a yield when the command declares no yields clause" in { (td: TestData) =>
      val input = RiddlParserInput(model("yield event E", yieldsClause = ""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        errors(msgs).exists(m =>
          m.message.contains("does not") && m.message.contains("'yields' clause")
        ) mustBe true
      }
    }
  }
}
