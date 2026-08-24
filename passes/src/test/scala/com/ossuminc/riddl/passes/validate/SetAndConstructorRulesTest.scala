/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Three rules about `set`, `morph` and constructors (Reid, 2026-08-24).
  *
  *   1. **Every constructor supplies every field.** *"If the constructor does not explicitly set
  *      every field, it is invalid. We don't want to guess what the default should be nor do we
  *      want to let old state values creep through."* Ruled for ALL constructors, not just state
  *      records, so there is no exception to remember.
  *   2. **No `set` may follow a `morph`** in the same clause — the state has changed, so those
  *      values belong in the morph's own record constructor or in the new state's `on` clauses.
  *   3. **`set state S` may only name the current state.**
  *
  * **Rule 2 is what makes rule 3 lexical.** riddl-generator warned that a lexical rule 3 would
  * misfire on 36 reactive-bbq sites where a `morph` had already transitioned, and asked for
  * flow-sensitive tracking with branch joins. Rule 2 makes that combination illegal outright, so
  * the current state is always the enclosing one and the analysis was never needed.
  */
class SetAndConstructorRulesTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  private def errs(msgs: Messages, frag: String): Messages =
    msgs.filter(m => m.isError && m.message.contains(frag))

  private def model(stmts: String, extraState: String = ""): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event E is { w: String(1,9) }
       |    record R is { a: String(1,9)  b: String(1,9)?  c: TimeStamp }
       |    entity Ent is {
       |      initial state S1 of record Ctx.R is {
       |        initial handler H is {
       |          on event Ctx.E is {
       |$stmts
       |          }
       |        }
       |      }
       |$extraState
       |    }
       |  }
       |}
       |""".stripMargin

  private val stateTwo =
    """      state S2 of record Ctx.R is {
      |        handler H2 is { on event Ctx.E is { do "ok" } }
      |      }""".stripMargin

  "a constructor omitting fields" should {
    "be an Error naming the missing ones" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set state S1 to record Ctx.R(a = "x")"""), td)
      val found = errs(msgs, "does not supply")
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("2 fields: b, c")
      }
    }

    "be satisfied when every field is supplied, `empty` included" in { (td: TestData) =>
      // The rule is only sayable because `empty` exists: before it, an optional field had no
      // spelling for absent, so omission was the ONLY way to say it.
      val msgs = messagesFor(
        model("""            set state S1 to record Ctx.R(a = "x", b = empty, c = prompt("now"))"""),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, "does not supply") mustBe empty }
    }

    "apply to a MESSAGE constructor too, not just a state record" in { (td: TestData) =>
      // Ruled for all constructors so there is no exception to remember.
      val msgs = messagesFor(
        model("""            set state S1 to record Ctx.R(a = "x", b = empty, c = prompt("now"))
                |            yield event Ctx.E()""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, "does not supply") must not be empty }
    }
  }

  "a `set` after a `morph`" should {
    "be an Error, naming the morph" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          """            morph entity Ctx.Ent to state Ctx.Ent.S2 with record Ctx.R(a = "x", b = empty, c = prompt("n"))
            |            set field R.a to "y"""".stripMargin,
          stateTwo
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "may not follow the 'morph'") must not be empty
      }
    }

    "be an Error even when the morph is inside a `when` branch" in { (td: TestData) =>
      // The branch may have run, so treating "may have morphed" as "has morphed" is the only
      // direction that cannot be wrong. This is reactive-bbq's KitchenTicket shape.
      val msgs = messagesFor(
        model(
          """            when "ready" then
            |              morph entity Ctx.Ent to state Ctx.Ent.S2 with record Ctx.R(a = "x", b = empty, c = prompt("n"))
            |            end
            |            set field R.a to "y"""".stripMargin,
          stateTwo
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "may not follow the 'morph'") must not be empty
      }
    }

    "draw nothing when the `set` comes BEFORE the morph" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          """            set field R.a to "y"
            |            morph entity Ctx.Ent to state Ctx.Ent.S2 with record Ctx.R(a = "x", b = empty, c = prompt("n"))""".stripMargin,
          stateTwo
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "may not follow the 'morph'") mustBe empty
      }
    }
  }

  "`set state` naming a state the entity is not in" should {
    "be an Error naming the current state" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          """            set state S2 to record Ctx.R(a = "x", b = empty, c = prompt("n"))""",
          stateTwo
        ),
        td
      )
      val found = errs(msgs, "is not the state this entity is in")
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("State 'S1'")
      }
    }

    "draw nothing when it names the enclosing state" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""            set state S1 to record Ctx.R(a = "x", b = empty, c = prompt("n"))""", stateTwo),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "is not the state this entity is in") mustBe empty
      }
    }

    "report ONCE when it follows a morph, not twice" in { (td: TestData) =>
      // The morph rule owns this case. Reporting the state rule too would attach a FALSE
      // explanation -- naming the enclosing state as current when the morph just changed it.
      // reactive-bbq had 29 such double-reports before this was fixed.
      val msgs = messagesFor(
        model(
          """            morph entity Ctx.Ent to state Ctx.Ent.S2 with record Ctx.R(a = "x", b = empty, c = prompt("n"))
            |            set state S2 to record Ctx.R(a = "z", b = empty, c = prompt("n"))""".stripMargin,
          stateTwo
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "may not follow the 'morph'") must not be empty
        errs(msgs, "is not the state this entity is in") mustBe empty
      }
    }
  }
}
