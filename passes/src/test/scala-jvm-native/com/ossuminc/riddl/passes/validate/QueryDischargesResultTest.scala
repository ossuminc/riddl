/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A query must answer — or refuse — on EVERY path (Reid, 2026-08-16: *"queries SHOULD be answered,
  * however, it is possible to let them refuse as well"*).
  *
  * The rule was "a reply appears ANYWHERE in the clause", so `when ready then reply result R end`
  * with no `else` was accepted while answering nothing on the other branch. **That is not a style
  * matter: `ask` is defined as taking the value a `reply` provides, so an unanswered path leaves
  * the caller waiting.**
  *
  * The refusal exemption makes this exactly PARALLEL to the command rule rather than stricter — a
  * clause that refuses has decided, and a refusal is an answer.
  */
class QueryDischargesResultTest extends AbstractValidatingTest {

  private def model(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    query Ask is { id: String }
       |    result Answer is { id: String }
       |    invariant Ready is "the thing is ready"
       |    entity Thing is {
       |      record Data is { id: String }
       |      state Live of record Dom.Ctx.Thing.Data is {
       |        initial handler H is {
       |          on query Dom.Ctx.Ask is {
       |$body
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def completenessOf(body: String, td: TestData): Seq[String] =
    var found = Seq.empty[String]
    parseAndValidateDomain(RiddlParserInput(model(body), td), shouldFailOnErrors = false) {
      case (_, _, messages) =>
        found = messages.filter(_.message.contains("should result in a reply")).map(_.message)
        succeed
    }
    found

  "a query handler" should {

    "be complete when it replies unconditionally" in { (td: TestData) =>
      completenessOf("""            reply result Dom.Ctx.Answer""", td) mustBe empty
    }

    // The gap being closed. Accepted before this: a reply exists SOMEWHERE, so the check passed,
    // while the implicit else-branch answers nothing and the caller waits forever.
    "be INCOMPLETE when it replies only on one branch" in { (td: TestData) =>
      val body =
        """            when "it is ready" then
          |              reply result Dom.Ctx.Answer
          |            end""".stripMargin
      completenessOf(body, td) must not be empty
    }

    "be complete when both branches reply" in { (td: TestData) =>
      val body =
        """            when "it is ready" then
          |              reply result Dom.Ctx.Answer
          |            else
          |              reply result Dom.Ctx.Answer
          |            end""".stripMargin
      completenessOf(body, td) mustBe empty
    }

    // Reid's ruling: a refusal IS an answer. This is what keeps the query rule parallel to the
    // command rule instead of stricter than it.
    "be complete when it refuses outright" in { (td: TestData) =>
      completenessOf("""            error "not available"""", td) mustBe empty
    }

    "be complete when one branch replies and the other refuses" in { (td: TestData) =>
      val body =
        """            when "it is ready" then
          |              reply result Dom.Ctx.Answer
          |            else
          |              error "not ready"
          |            end""".stripMargin
      completenessOf(body, td) mustBe empty
    }

    "be complete when it refuses via require" in { (td: TestData) =>
      completenessOf("""            require invariant Dom.Ctx.Ready""", td) mustBe empty
    }
  }
}
