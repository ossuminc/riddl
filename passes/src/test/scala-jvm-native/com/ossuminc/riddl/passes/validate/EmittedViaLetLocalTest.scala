/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** [1.2]: an event emitted ONLY through a widened operand — a `let`-local rather than a bare
  * message reference — counts as emitted.
  *
  * `emittedMessageTypes` is a single flat `Finder` sweep over the whole root, so a `send`/`tell`
  * it finds carries no notion of which clause it came from, let alone that clause's `let` scope.
  * It therefore resolved operands with the NARROW `operandType` and could not see through a
  * `ValueRef` at all. The consequence was a FALSE POSITIVE: a correlation folding such an event
  * drew "no processor in this model emits it" while something plainly did.
  *
  * The fix reuses `ValidationOutput.deliverableTypes`, filled during the traversal by
  * `checkStatementScopes`, which visits one clause at a time WITH its scope. The sweep stays flat
  * — that is the right shape for a whole-root question — and only the resolution changed.
  */
class EmittedViaLetLocalTest extends AbstractValidatingTest {

  /** The event is emitted through a `let`-local, which is the shape the narrow resolution missed.
    * The correlation is what makes the advisory run at all.
    */
  private val src =
    """domain D is {
      |  context C is {
      |    command Place is { id: String }
      |    event Shipped is { id: String }
      |    command RecordShipment is { id: String }
      |    record ShipView is { id: String }
      |    entity Order is {
      |      handler H is {
      |        on command D.C.Place is {
      |          let ship: D.C.Shipped = prompt("the shipment event for this order")
      |          yield ship
      |        }
      |      }
      |    }
      |    repository R is {
      |      handler RH is { on command D.C.RecordShipment is { ??? } }
      |    }
      |    projector P is {
      |      record PView is { id: String }
      |      correlation ByOrder by id yields command D.C.RecordShipment is {
      |        handler Collect is {
      |          on event D.C.Shipped is { do "accumulate the shipment" }
      |        }
      |      } times out after "1 hour" { error "shipment never completed" }
      |    }
      |  }
      |}
      |""".stripMargin

  "an event emitted only through a let-local" should {
    "not be reported as emitted by nothing" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs: Messages.Messages) =>
          val unemitted = msgs.filter(m => m.message.contains("Shipped") && m.message.contains("emit"))
          withClue(s"messages were:\n${msgs.format}\n") { unemitted mustBe empty }
      }
    }
  }
}
