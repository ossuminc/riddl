/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A value reference must name a VALUE (synapify, 2026-11-17).
  *
  * reactive-bbq validated with 0 errors while two constructors wrote `menuReleaseStatus = Draft`,
  * and the only `Draft` in the model is an entity STATE in another context. The resolver's last
  * resort for a name in no value scope is an ordinary lookup of any definition, so the reference
  * bound to the state, and the argument type check had nothing to compare.
  *
  * The admitted kinds come from a census of all 20,076 value references in riddl-models: Field,
  * Enumerator, and Type only as a clause binding naming the whole message. Constant is admitted by
  * definition. Each admitted kind has a case here, so narrowing the rule reddens a test.
  */
class ValueRefNotAValueTest extends AbstractValidatingTest {

  private def model(arg: String): String =
    s"""domain D is {
       |  context C is {
       |    type Status is any of { ReleaseDraft, ReleaseFinal }
       |    constant DefaultStatus: String = "draft"
       |    command Make is { s: Status, note: String }
       |    command Go is { note: String }
       |    entity Other is {
       |      record OR is { x: Integer }
       |      state Draft of record OR is { handler OH is { on command Make { ??? } } }
       |    }
       |    entity T is {
       |      handler H is {
       |        on go: command Go { tell command Make($arg) to entity C.Other }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def notAValue(src: String, td: TestData): Seq[Messages.Message] =
    val msgs = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
      case Right(result) => result.messages
      case Left(msgs)    => msgs
    msgs.filter(_.ruleId.contains(RuleId.ValueRefNotAValue))

  "A value reference" should {
    "be an Error when it names a State (synapify's reactive-bbq shape)" in { (td: TestData) =>
      val errs = notAValue(model("s = Draft, note = go.note"), td)
      errs.size mustBe 1
      errs.head.kind mustBe Messages.Error
      errs.head.message must include("State 'Draft'")
    }

    "be an Error when it names an Entity" in { (td: TestData) =>
      notAValue(model("s = Status.ReleaseDraft, note = Other"), td).size mustBe 1
    }

    "be an Error when it names a Type that is not a clause binding" in { (td: TestData) =>
      notAValue(model("s = Status, note = go.note"), td).size mustBe 1
    }

    "admit an Enumerator, a message Field and a Constant" in { (td: TestData) =>
      notAValue(model("s = Status.ReleaseDraft, note = go.note"), td) mustBe empty
      notAValue(model("s = Status.ReleaseDraft, note = DefaultStatus"), td) mustBe empty
    }

    "admit a clause binding naming the whole message" in { (td: TestData) =>
      val src = model("s = Status.ReleaseDraft, note = go.note")
        .replace("on go: command Go { tell", "on go: command Go { log go tell")
      notAValue(src, td) mustBe empty
    }
  }
}
