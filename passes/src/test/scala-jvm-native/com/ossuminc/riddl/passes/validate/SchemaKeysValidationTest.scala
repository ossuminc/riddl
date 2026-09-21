/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** B3 (2026-09-21): a key names a field of a STORED record; a keyed schema is not unindexed;
  * `with history` validates clean.
  */
class SchemaKeysValidationTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def of(msgs: Messages, rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  /** A queried repository; `schemaLines` are the lines after the `of` entry. */
  private def model(schemaLines: String, dataLine: String = "of tickets as record Tickets.StoredTicket"): String =
    s"""domain Kitchen is {
       |  context Tickets is {
       |    record StoredTicket is { ticketId: String, status: String } with { briefly "t" }
       |    record Elsewhere is { other: String } with { briefly "e" }
       |    query FindTicket replies result TicketFound is { ticketId: String } with { briefly "q" }
       |    result TicketFound is { ticketId: String, status: String } with { briefly "r" }
       |    repository Store is {
       |      schema S is relational
       |        $dataLine
       |$schemaLines
       |      with { briefly "s" }
       |      inlet In is query Tickets.FindTicket with { briefly "i" }
       |      outlet Out is result Tickets.TicketFound with { briefly "o" }
       |      handler H is {
       |        on q: query Tickets.FindTicket is { reply result Tickets.TicketFound(ticketId = q.ticketId, status = "open") }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "schema keys" should {

    "validate clean when the key names a stored record's field, and silence the no-index warning" in {
      (td: TestData) =>
        val msgs = diagnostics(model("        key on field Tickets.StoredTicket.ticketId"), td.name)
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
        of(msgs, RuleId.QueriedWithoutIndex) mustBe empty
    }

    "still report a queried schema with neither key nor index (control)" in { (td: TestData) =>
      of(diagnostics(model(""), td.name), RuleId.QueriedWithoutIndex).size mustBe 1
    }

    "refuse a key on a field of a record the schema does not store" in { (td: TestData) =>
      val msgs = diagnostics(model("        key on field Tickets.Elsewhere.other"), td.name)
      val found = of(msgs, RuleId.SchemaKeyNotStoredField)
      found.size mustBe 1
      found.head.message must include("Tickets.Elsewhere.other")
      found.head.message must include("Tickets.StoredTicket")
    }

    "accept `with history` on a data entry" in { (td: TestData) =>
      val msgs = diagnostics(
        model("        key on field Tickets.StoredTicket.ticketId",
          dataLine = "of tickets as record Tickets.StoredTicket with history"),
        td.name
      )
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }
  }
}
