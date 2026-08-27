/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** [2.3]: `ResolutionPass.valueScopeField`'s `aggFields` reads a Type's fields with
  * `case ate: AggregateTypeExpression => ate.fields; case _ => Seq.empty` — an empty answer in a
  * RESOLUTION position, which downstream reads as "no such field".
  *
  * The question this pins is whether that empty is reachable through an ALIAS. `isAddressFieldFor`
  * had exactly this defect and was taught to follow alias chains in `ccd278c00`, on the grounds
  * that aliasing is riddl-models' documented house style — so the shape was a strong suspect.
  *
  * **VERDICT 2026-08-18: NOT a defect, and this suite is the evidence.** An aliased state record
  * resolves exactly as a direct one does, because `valueScopeField` is not the only route — the A55
  * `ValueRef` walk reaches the field anyway. So the `case _ => Seq.empty` is the "nothing to do
  * here" kind the no-silent-fall-through rule explicitly permits, not the "I do not know what this
  * is" kind.
  *
  * Kept rather than deleted: it costs nothing and it pins the ONE thing that would make the empty
  * bite — a future change that made `valueScopeField` the sole route would turn these green cases
  * red instead of turning riddl-models red.
  */
class AliasedValueScopeTest extends AbstractValidatingTest {

  private def errorsFor(src: String, td: TestData): Messages.Messages =
    var captured: Messages.Messages = Messages.empty
    parseAndValidateInput(RiddlParserInput(src, td), shouldFailOnErrors = false) {
      case (_, _, msgs) => captured = msgs; succeed
    }
    captured

  "a bare value reference to a field of an ALIASED handled message" should {
    "resolve" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    command Direct is { amount: Real }
          |    entity E is {
          |      handler H is {
          |        on command D.C.Direct is {
          |          let a = amount
          |          do "use it"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val errs = errorsFor(src, td).justErrors
      withClue(s"CONTROL (no alias) messages:\n${errorsFor(src, td).format}\n") {
        errs mustBe empty
      }
    }
  }

  "a bare value reference to a field of an ALIASED entity state record" should {
    "resolve, exactly as it does when the state names the record directly" in { (td: TestData) =>
      val direct =
        """domain D is {
          |  context C is {
          |    command Touch is { ??? }
          |    record OrderData is { total: Real }
          |    entity E is {
          |      state main of record D.C.OrderData
          |      handler H is {
          |        on command D.C.Touch is {
          |          let t = total
          |          do "use it"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val aliased =
        """domain D is {
          |  context C is {
          |    command Touch is { ??? }
          |    record OrderData is { total: Real }
          |    type OrderState is D.C.OrderData
          |    entity E is {
          |      state main of record D.C.OrderState
          |      handler H is {
          |        on command D.C.Touch is {
          |          let t = total
          |          do "use it"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin

      val directErrs = errorsFor(direct, td).justErrors
      withClue(s"DIRECT form messages:\n${errorsFor(direct, td).format}\n") {
        directErrs mustBe empty
      }
      val aliasErrs = errorsFor(aliased, td).justErrors
      withClue(s"ALIASED form messages:\n${errorsFor(aliased, td).format}\n") {
        aliasErrs mustBe empty
      }
    }
  }
}
