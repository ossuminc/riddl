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
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** B5 (2026-09-23): the collection must be one, the element binds into the predicate ONLY, the
  * predicate must be boolean, a count is a `Whole`, and a membership test is type-checked
  * against the element type.
  */
class CollectionPredicateValidationTest extends AbstractValidatingTest {

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

  private def model(body: String): String =
    s"""domain Kitchen is {
       |  context Tickets is {
       |    record Item is { menuItemId: String, ready: Boolean, category: String } with { briefly "i" }
       |    record Data is { ticketId: String, items: Tickets.Item*, zips: String*, howMany: Whole,
       |      tight: Natural, flag: Boolean } with { briefly "d" }
       |    command Mark yields event Marked is { ticketId: String } with { briefly "c" }
       |    entity Ticket is {
       |      event Marked is { ticketId: String } with { briefly "e" }
       |      state S of record Tickets.Data
       |      inlet In is command Tickets.Mark with { briefly "i" }
       |      outlet Out is event Ticket.Marked with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Ticket.Marked(ticketId = "x") }
       |        on m: command Tickets.Mark is {
       |          $body
       |          yield event Ticket.Marked(ticketId = m.ticketId)
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "t" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "collection predicates" should {

    "validate riddl-generator's kitchen guard clean" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""when all of Data.items as item where item.ready then do "ready" end"""), td.name)
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "refuse a non-collection operand" in { (td: TestData) =>
      val found = of(
        diagnostics(model("""when any of Data.ticketId as i where i.ready then do "x" end"""), td.name),
        RuleId.NotACollection
      )
      found.size mustBe 1
      found.head.message must include("'Data.ticketId'")
    }

    "require the predicate to be boolean" in { (td: TestData) =>
      diagnostics(model("""when all of Data.items as i where i.menuItemId then do "x" end"""), td.name)
        .justErrors.map(_.message).mkString must include("must be a boolean")
    }

    "bind the element in the predicate and NOT after it" in { (td: TestData) =>
      // `item` is in scope inside the predicate…
      val ok = diagnostics(
        model("""when any of Data.items as item where item.category == "drink" then do "x" end"""),
        td.name
      )
      withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
      // …and not outside it
      val bad = diagnostics(
        model("""let x = any of Data.items as item where item.ready
                |          let y = item.ready""".stripMargin),
        td.name
      )
      bad.justErrors.map(_.message).mkString must include("item")
    }
  }

  "count, filter and contains" should {

    "type a count as a Whole" in { (td: TestData) =>
      // Assigning into a Whole field is clean.
      val ok = diagnostics(model("""set field Tickets.Data.howMany to count of Data.items"""), td.name)
      withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
      // The TYPE itself is read off B4's arithmetic mismatch, which names both operands --
      // `set` type-checks only NAMED types, so a predefined `Natural` field would not catch it
      // (a documented limitation of `checkValueType`, not of this feature).
      val probe = diagnostics(model("""let x = count of Data.items + "s""""), td.name)
      val found = of(probe, RuleId.ArithmeticOperandMismatch)
      found.size mustBe 1
      found.head.message must include("'Whole' value")
    }

    "refuse a filter where a boolean is wanted -- at PARSE, not validation" in { (td: TestData) =>
      // A filter is not a `BooleanExpression`, and a `when` condition is filtered to one
      // (`booleanExprOnly`), so the grammar refuses it before validation ever sees it. That is
      // the better of the two outcomes and is pinned here so a later widening cannot quietly
      // make `when <filter>` mean something.
      val src = model("""when Data.items as i where i.ready then do "x" end""")
      TopLevelParser.parseInput(RiddlParserInput(src, td), true).isLeft mustBe true
    }

    "type-check membership against the element type" in { (td: TestData) =>
      val ok = diagnostics(model("""when Data.zips contains m.ticketId then do "x" end"""), td.name)
      withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
      of(diagnostics(model("""when Data.zips contains Data.howMany then do "x" end"""), td.name),
        RuleId.ValueTypeMismatch).size mustBe 1
    }

    "count a filter" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""when count of (Data.items as i where i.ready) > 0 then do "some" end"""), td.name)
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }
  }
}
