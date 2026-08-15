/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A17's ASK form (`when invariant X`) inside the three value-walking dispatches.
  *
  * Reported by riddl-generator against 2.0.0-rc.12, which THREW rather than validating:
  * `stateReadsIn`, `asksIn` and `countValueFailPoints` each enumerate the `Value` union with a
  * trailing `throw` instead of a catch-all, and none had an arm for [[AST.InvariantCondition]]. The
  * throws were doing their job — a missing arm is meant to fail loudly rather than silently return
  * "nothing here" — so the defect was the enumeration, not the design.
  *
  * The gap was NOT introduced all at once: `stateReadsIn` shipped in rc.12, `asksIn` in rc.11
  * (`f7724a3f0`) and `countValueFailPoints` before rc.1. riddlg hit it only when a spec used
  * `when invariant X` in an entity handler, which is what reaches the newest of the three.
  *
  * **Each case here asserts the RECURSION, not just the absence of a crash.** A `case ic:
  * InvariantCondition => Seq.empty` would stop the throw and still be wrong, and every
  * crash-only test would pass against it. So the `with <expr>` operand carries something each
  * dispatch is looking for, and the case asserts that it was found.
  */
class InvariantConditionValueWalkTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def textFor(src: String, origin: String): String =
    diagnostics(src, origin).map(_.message).mkString("\n")

  "`when invariant X` in an entity handler" should {

    "validate instead of throwing" in { (td: TestData) =>
      // The reproducer riddl-generator sent, reduced. Before the fix this aborted ValidationPass
      // with "stateReadsIn has no arm for InvariantCondition"; a Severe with a stack trace, not a
      // rejection of the model.
      val src =
        """domain Shop is {
          |  context Sales is {
          |    command Reopen is { id: String }
          |    entity Order is {
          |      record ClosedState is { total: Integer, floor: Integer }
          |      invariant NonNegative is total >= floor
          |      initial state Done of record Order.ClosedState is {
          |        handler dhr is {
          |          on command Reopen {
          |            when invariant NonNegative then {
          |              set field Order.ClosedState.total to "1"
          |            } end
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = diagnostics(src, "invariant-condition-entity")
      msgs.filter(_.kind == Messages.SevereError) mustBe empty
      msgs.justErrors mustBe empty
    }

    "still find a `get from state` hidden in the invariant's `with` operand" in { (td: TestData) =>
      // THE case that distinguishes a real fix from `=> Seq.empty`. The state read belongs to a
      // DIFFERENT entity, so a walk that reaches it reports the encapsulation Error and a walk
      // that stops at the InvariantCondition reports nothing at all.
      val src =
        """domain Shop is {
          |  context Sales is {
          |    command Reopen is { id: String }
          |    record Data is { total: Integer, floor: Integer }
          |    invariant NonNegative is total >= floor
          |    entity Peer is {
          |      state PS of record Data is { handler PH is { on init is { do "x" } } }
          |    }
          |    entity Order is {
          |      state Done of record Data is {
          |        handler dhr is {
          |          on command Reopen {
          |            when invariant NonNegative with get from state Peer.PS then {
          |              set field Data.total to "1"
          |            } end
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val text = textFor(src, "invariant-condition-hides-state-read")
      text must include("does not own")
    }
  }

  "`when !<identifier>`" should {

    "validate instead of throwing" in { (td: TestData) =>
      // Reported by ossum.tech 2026-08-13 against rc.13. `stateReadsIn` gained arms for the value
      // kinds that were NOTICED; `Identifier` was not among them, so a documented form that
      // validated on rc.11 threw on rc.13.
      //
      // The lesson is narrower than "enumerate the sealed hierarchy" -- that was already the rule
      // and it was followed. `statementValues` yields a domain WIDER than `Value`:
      // `WhenStatement.condition` is `LiteralString | Identifier | ValueRef | BooleanExpression |
      // PromptValue`, and `Identifier` appears in no other member. Auditing `Value` alone misses
      // exactly this. Enumerate the domain of the FUNCTION, not of the nearest-looking type.
      //
      // UPDATE (2026-08-15, not/! synonymy task 2): `when !isValid` no longer builds a bare
      // Identifier condition at all -- since task 1 it parses to `NotExpression(ValueRef)`, and
      // `WhenStatement.negated` (the flag this test's name used to describe) is deleted. This case
      // now exercises the `NotExpression`/`ValueRef` arms of `stateReadsIn`, not the `Identifier`
      // arm; the `Identifier` arm remains reachable only via directly-constructed ASTs (e.g. an
      // older BAST/JSON payload), kept for back-compat per `StatementParser.whenCondition`.
      val src =
        """domain D is {
          |  author A is { name is "R" email is "r@o.com" }
          |  context C is {
          |    record Acct is { isValid is Boolean } with { briefly "r" }
          |    command Plain is { amount is Natural } with { briefly "c" }
          |    entity E is {
          |      state S of record Acct is {
          |        handler H is {
          |          on command Plain {
          |            when !isValid then { error "no" } end
          |          }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "ctx" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "when-negated-identifier")
      msgs.filter(_.kind == Messages.SevereError) mustBe empty
      msgs.justErrors mustBe empty
    }

    "CONTROL: the un-negated form still validates too" in { (td: TestData) =>
      // Guards against a fix that made `when` conditions unreachable rather than handled.
      val src =
        """domain D is {
          |  author A is { name is "R" email is "r@o.com" }
          |  context C is {
          |    record Acct is { isValid is Boolean } with { briefly "r" }
          |    command Plain is { amount is Natural } with { briefly "c" }
          |    entity E is {
          |      state S of record Acct is {
          |        handler H is {
          |          on command Plain {
          |            when isValid then { error "no" } end
          |          }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "ctx" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "when-plain-identifier")
      msgs.filter(_.kind == Messages.SevereError) mustBe empty
      msgs.justErrors mustBe empty
    }

    "the RECOMMENDED spelling `when not <ref>` works, which is why `!` is not extended" in {
      (td: TestData) =>
        // Pins the ruling recorded in CLAUDE.md (2026-08-13): `not` is the only general-purpose
        // negation and `!` stays `when`-only and identifier-only. That ruling is only defensible
        // while `not` genuinely covers the same ground, so assert it rather than assume it.
        val src =
          """domain D is {
            |  author A is { name is "R" email is "r@o.com" }
            |  context C is {
            |    record Acct is { isValid is Boolean } with { briefly "r" }
            |    command Plain is { amount is Natural } with { briefly "c" }
            |    entity E is {
            |      state S of record Acct is {
            |        handler H is {
            |          on command Plain {
            |            when not isValid then { error "no" } end
            |          }
            |        } with { briefly "h" }
            |      } with { briefly "s" }
            |    } with { briefly "e" }
            |  } with { briefly "ctx" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "when-not-keyword")
        msgs.filter(_.kind == Messages.SevereError) mustBe empty
        msgs.justErrors mustBe empty
    }
  }

  "`when invariant X` in a saga step" should {

    "validate instead of throwing" in { (td: TestData) =>
      // Reaches `asksIn`, whose gap predates rc.12 and had simply never been hit.
      val src =
        """domain Shop is {
          |  context Sales is {
          |    command Go is { id: String }
          |    record Data is { total: Integer, floor: Integer }
          |    invariant NonNegative is total >= floor
          |    entity Worker is { handler WH is { on command Go is { do "work" } } }
          |    saga Sg is {
          |      step One is {
          |        when invariant NonNegative then { tell command Go(id = "the id") to entity Worker } end
          |      } reverted by { do "undo it" }
          |      step Two is { do "something" } reverted by { do "undo it" }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = diagnostics(src, "invariant-condition-saga")
      msgs.filter(_.kind == Messages.SevereError) mustBe empty
      msgs.justErrors mustBe empty
    }

    "still find an `ask` hidden in the invariant's `with` operand" in { (td: TestData) =>
      // The `asksIn` twin of the state-read case: a saga may not `ask`, not even one buried in an
      // invariant's operand, and a non-recursing arm would let it through silently.
      val src =
        """domain Shop is {
          |  context Sales is {
          |    command Go is { id: String }
          |    query Q is { why: String }
          |    result R is { answer: String }
          |    record Data is { total: Integer, floor: Integer }
          |    invariant NonNegative is total >= floor
          |    entity Worker is {
          |      handler WH is {
          |        on command Go is { do "work" }
          |        on query Q is { reply result R }
          |      }
          |    }
          |    saga Sg is {
          |      step One is {
          |        when invariant NonNegative with ask query Q of entity Worker then {
          |          tell command Go(id = "the id") to entity Worker
          |        } end
          |      } reverted by { do "undo it" }
          |      step Two is { do "something" } reverted by { do "undo it" }
          |    }
          |  }
          |}
          |""".stripMargin
      textFor(src, "invariant-condition-hides-ask") must include("may not 'ask'")
    }
  }
}
