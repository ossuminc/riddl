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

/** Message-value-source design, Task 1 review round 1: `checkMessageOperandSource` widened what
  * `send`/`tell` ACCEPT as an operand, but several other checks read a `send`/`tell` operand's
  * resolved TYPE via `operandType`/`operandMessageKind` — which stayed narrow (on-clause binding
  * only) and so went BLIND to a widened-source operand rather than merely leaving it unwidened.
  * That is a correctness regression this task introduced, not pre-existing debt: before the
  * widening, `checkBoundMessageOperand` rejected these operands outright, so the consumer checks
  * below never had occasion to run against them.
  *
  * Two classes of finding, fixed the same way (`widenedOperandType`/`widenedOperandMessageKind`,
  * threaded with the LEXICAL `let` scope of the specific statement):
  *
  *   - CRITICAL: `checkTellAddressing`'s `by`-field Error and ambiguity Error went unreachable for
  *     a widened-source `tell` — a malformed `by` or a genuinely ambiguous address validated clean.
  *   - IMPORTANT: three Completeness checks (command→event, query→result, saga step
  *     "do-statements contain a tell command") FALSE-POSITIVE on code that satisfies them only via
  *     a widened operand, because `operandMessageKind` answered `None` for it.
  *
  * Every case here is proven load-bearing in the task-1 (round 1 fix) report by reverting the
  * consumer-side fix and confirming each one reverts to the WRONG outcome the review found.
  */
class MessageOperandSourceConsumerFixTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, showCompletenessWarnings = true)
    ) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  "checkTellAddressing on a widened-source tell (Critical)" should {

    "still Error when 'by' names a field that is not Id(target)" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Go is { why: String } with { briefly "g" }
          |    command Ship is { orderId: Id(entity Order) } with { briefly "s" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go {
          |            let oid = initiate entity Order
          |            let m = command Ship(orderId = oid)
          |            tell m to entity Order by nonexistentField
          |          }
          |        } with { briefly "ch" }
          |      } with { briefly "ce" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, td.name).justErrors.map(_.message).mkString("\n")
      text must include("nonexistentField")
    }

    "still Error on a genuine ambiguity with no 'by'" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Go is { why: String } with { briefly "g" }
          |    command Ship is {
          |      fromOrder: Id(entity Order), toOrder: Id(entity Order)
          |    } with { briefly "s" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go {
          |            let f = initiate entity Order
          |            let t = initiate entity Order
          |            let m = command Ship(fromOrder = f, toOrder = t)
          |            tell m to entity Order
          |          }
          |        } with { briefly "ch" }
          |      } with { briefly "ce" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, td.name).justErrors.map(_.message).mkString("\n")
      text must include("fromOrder")
      text must include("toOrder")
      text must include("ambiguous")
    }
  }

  "the command->event completeness check on a widened-source tell (Important)" should {

    "NOT false-positive when the event is emitted only via a let-local" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity Ledger is {
          |      record Fields is { data: String }
          |      state Main of record Ledger.Fields
          |      handler LH is { on event D.C.Evt { set field Main.data to "x" } }
          |    }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on command D.C.Cmd {
          |          let evt = event D.C.Evt(data = "shipped")
          |          tell evt to entity D.C.Ledger
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val cw = diagnostics(src, td.name).filter(_.isCompleteness)
      cw.exists(_.message.contains("should result in sending an event")) mustBe false
    }

    // The control: without a widened-source send/tell/yield at all, the warning must still fire.
    // Pins the OTHER half -- a check that never runs would also make the case above pass.
    "still fire when nothing emits the event" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    command Cmd is { data: String }
          |    event Evt is { data: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on command D.C.Cmd {
          |          set field Main.data to "updated"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val cw = diagnostics(src, td.name).filter(_.isCompleteness)
      cw.exists(_.message.contains("should result in sending an event")) mustBe true
    }
  }

  "the query->result completeness check on a widened-source tell (Important)" should {

    "NOT false-positive when the result is produced only via a let-local" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    query Qry is { id: String }
          |    result Ans is { data: String }
          |    entity Ledger is {
          |      record Fields is { data: String }
          |      state Main of record Ledger.Fields
          |      handler LH is { on command D.C.Cmd { do "noop" } }
          |    }
          |    command Cmd is { x: String }
          |    entity E is {
          |      record Fields is { data: String }
          |      state Main of record E.Fields
          |      handler H is {
          |        on query D.C.Qry {
          |          let ans = result D.C.Ans(data = "42")
          |          tell ans to entity D.C.Ledger
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val cw = diagnostics(src, td.name).filter(_.isCompleteness)
      cw.exists(_.message.contains("should result in a reply or sending a result")) mustBe false
    }
  }

  "the saga-step tell-command completeness check on a widened-source tell (Important)" should {

    "NOT false-positive when the command is told only via a let-local" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ours is {
          |    command Doit is { x: Integer } with { briefly "d" }
          |    entity Caller is {
          |      handler H is {
          |        on command Dom.Ours.Doit is { do "noop" }
          |      } with { briefly "h" }
          |    } with { briefly "c" }
          |    saga Flow is {
          |      step One is {
          |        let cmd = command Dom.Ours.Doit(x = "1")
          |        tell cmd to entity Dom.Ours.Caller
          |      } reverted by { do "undo" } with { briefly "s1" }
          |    } with { briefly "f" }
          |  } with { briefly "ctx" }
          |} with { briefly "dom" }
          |""".stripMargin
      val cw = diagnostics(src, td.name).filter(_.isCompleteness)
      cw.exists(_.message.contains("do-statements contain no 'tell command'")) mustBe false
    }

    // The control, mirroring the command->event one above.
    "still fire when the do-statements never tell a command" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ours is {
          |    command Doit is { x: Integer } with { briefly "d" }
          |    entity Caller is {
          |      handler H is {
          |        on command Dom.Ours.Doit is { do "noop" }
          |      } with { briefly "h" }
          |    } with { briefly "c" }
          |    saga Flow is {
          |      step One is {
          |        do "nothing useful"
          |      } reverted by { do "undo" } with { briefly "s1" }
          |    } with { briefly "f" }
          |  } with { briefly "ctx" }
          |} with { briefly "dom" }
          |""".stripMargin
      val cw = diagnostics(src, td.name).filter(_.isCompleteness)
      cw.exists(_.message.contains("do-statements contain no 'tell command'")) mustBe true
    }
  }
}
