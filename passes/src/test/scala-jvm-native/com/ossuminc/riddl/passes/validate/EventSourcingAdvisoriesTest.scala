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

/** Four counts over what an entity does with the journal it asked for (riddl-generator's task of
  * 2026-09-12; Reid's rulings the same day). Three are ADVISORIES -- structural facts consistent
  * with the model and inconsistent with what `event-sourced` (or its absence) usually means -- and
  * one is Completeness. Every positive has a negative in the same family, and rule 1's negative is
  * the task's own: a single-state entity whose events a projector handles must NOT fire, or the
  * rule mis-fires on six of reactive-bbq's thirteen.
  */
class EventSourcingAdvisoriesTest extends AbstractValidatingTest {

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

  /** An entity `Acct` with one state and one command/event pair; `intent` is `event-sourced` or
    * empty; `fold` is the `on event` clause body; `reader` is an optional sibling that handles the
    * event.
    */
  private def single(intent: String, fold: String, reader: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    command Open yields event Opened is { id: String, balance: Integer } with { briefly "c" }
       |    event Opened is { id: String, balance: Integer } with { briefly "e" }
       |    $intent entity Acct is {
       |      record Fields is { id: String, balance: Integer } with { briefly "f" }
       |      state Main of record Acct.Fields
       |      inlet In is command Open with { briefly "i" }
       |      outlet Out is event Opened with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Opened(id = "x", balance = 0) }
       |        on o: command Open is { yield event Opened(id = o.id, balance = o.balance) }
       |        on ev: event Opened is { $fold }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |$reader
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private val projectorReader: String =
    """    projector Ledger is {
      |      record View is { id: String } with { briefly "v" }
      |      inlet PIn is event Opened with { briefly "i" }
      |      handler PH is { on event Opened is { do "project it" } } with { briefly "h" }
      |    } with { briefly "p" }""".stripMargin

  private val proseFold = """set state Acct.Main to prompt("apply Opened to the account state")"""
  private val snapshotFold = "set field Acct.Fields.id to ev.id set field Acct.Fields.balance to ev.balance"
  private val derivedFold = """set field Acct.Fields.balance to prompt("balance plus the amount")"""

  "rule 1, an event-sourced entity whose history nothing reads" should {

    "fire when one state, no transitions, and no other processor handles its events" in {
      (td: TestData) =>
        val found = of(diagnostics(single("event-sourced", snapshotFold), td.name),
          RuleId.EntityEventSourcedUnreadHistory)
        found.size mustBe 1
        found.head.kind mustBe Advisory
        found.head.message must include("1 events") // the count it took, N = 1
    }

    "NOT fire when a projector handles its events (the task's own negative control)" in {
      (td: TestData) =>
        of(diagnostics(single("event-sourced", snapshotFold, projectorReader), td.name),
          RuleId.EntityEventSourcedUnreadHistory) mustBe empty
    }
  }

  "rule 3, an event-sourced entity with a prose fold" should {

    "be a COMPLETENESS warning, since that replay has no defined semantics" in { (td: TestData) =>
      val found = of(diagnostics(single("event-sourced", proseFold, projectorReader), td.name),
        RuleId.EntityEventSourcedProseFolds)
      found.size mustBe 1
      found.head.kind mustBe CompletenessWarning
      found.head.message must include("1 of its 1")
    }

    "fire on ANY prose fold, counting them -- the corpus's real creation fold does not excuse the rest" in {
      (td: TestData) =>
        // The task wrote the rule as `forall folds: prose`; measured on reactive-bbq that fires on
        // NONE of the six entities it named, because each has one real creation fold beside five
        // prose ones. The fact is per fold, so the rule counts.
        val src = single("event-sourced", snapshotFold, projectorReader).replace(
          """        on other is { error "unexpected" }""",
          """        on event Opened is { set state Acct.Main to prompt("apply Opened again") }
            |        on other is { error "unexpected" }""".stripMargin
        )
        val found = of(diagnostics(src, td.name), RuleId.EntityEventSourcedProseFolds)
        found.size mustBe 1
        found.head.message must include("1 of its 2")
    }

    "NOT fire when a fold sets a field from the event" in { (td: TestData) =>
      of(diagnostics(single("event-sourced", snapshotFold, projectorReader), td.name),
        RuleId.EntityEventSourcedProseFolds) mustBe empty
    }

    "NOT fire when a fold sets a STATED FIELD from a prompt -- the language's own arithmetic (ruled 2026-09-14)" in {
      (td: TestData) =>
        // `set field S.balance to prompt("balance + points")`: RIDDL does no arithmetic by design
        // (2026-08-23), so this IS the intended spelling. The fold names what it changes; only the
        // operation is prose. Derived, not prose. `set state S to prompt(...)` stays prose (above).
        of(diagnostics(single("event-sourced", derivedFold, projectorReader), td.name),
          RuleId.EntityEventSourcedProseFolds) mustBe empty
    }
  }

  "rule 2, an event-sourced entity whose events are snapshots" should {

    "fire when every event carries the whole state and every fold copies it back" in {
      (td: TestData) =>
        val found = of(diagnostics(single("event-sourced", snapshotFold, projectorReader), td.name),
          RuleId.EntityEventSourcedSnapshotEvents)
        found.size mustBe 1
        found.head.kind mustBe Advisory
    }

    "NOT fire when a fold is derived" in { (td: TestData) =>
      of(diagnostics(single("event-sourced", derivedFold, projectorReader), td.name),
        RuleId.EntityEventSourcedSnapshotEvents) mustBe empty
    }
  }

  /** Multi-state, with a transition, for rule 1'. `intent` again event-sourced or empty. */
  private def multi(intent: String, reader: String): String =
    s"""domain D is {
       |  context C is {
       |    command Open yields event Opened is { id: String } with { briefly "c" }
       |    event Opened is { id: String } with { briefly "e" }
       |    $intent entity Acct is {
       |      record Fields is { id: String } with { briefly "f" }
       |      state Fresh of record Acct.Fields
       |      state Live of record Acct.Fields
       |      inlet In is command Open with { briefly "i" }
       |      outlet Out is event Opened with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Opened(id = "x") }
       |        on o: command Open is {
       |          yield event Opened(id = o.id)
       |          morph entity C.Acct to state Acct.Live with record C.Acct.Fields(id = o.id)
       |        }
       |        on ev: event Opened is { set field Acct.Fields.id to ev.id }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |$reader
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "rule 1', a CRUD entity whose transitions are consumed" should {

    "fire when not event-sourced, several states, a transition, and other processors read it" in {
      (td: TestData) =>
        val found = of(diagnostics(multi("", projectorReader), td.name),
          RuleId.EntityCrudWithTransitionsConsumed)
        found.size mustBe 1
        found.head.kind mustBe Advisory
        found.head.message must include("2 states")
    }

    "NOT fire on the event-sourced version of the same shape" in { (td: TestData) =>
      of(diagnostics(multi("event-sourced", projectorReader), td.name),
        RuleId.EntityCrudWithTransitionsConsumed) mustBe empty
    }

    "NOT fire when nothing else reads its events" in { (td: TestData) =>
      of(diagnostics(multi("", ""), td.name), RuleId.EntityCrudWithTransitionsConsumed) mustBe empty
    }
  }
}
