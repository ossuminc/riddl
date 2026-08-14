/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.TestData

/** A19↔A22 conformance: a command/query type's declarative `yields` clause and the `yield`
  * statement that produces the response must agree. A command/query that declares `yields M` must
  * be handled by a clause that yields exactly `M`; a `yield` with no matching contract is an error.
  */
class YieldConformanceTest extends AbstractValidatingTest {

  private def errors(msgs: Messages.Messages): Messages.Messages = msgs.filter(_.isError)

  private def model(handlerBody: String, yieldsClause: String = "yields event E"): String =
    s"""domain D is {
       |  context C is {
       |    event E is { data: String }
       |    event Other is { data: String }
       |    command Cmd $yieldsClause is { data: String }
       |    handler H is {
       |      on command D.C.Cmd { $handlerBody }
       |    }
       |  }
       |}
       |""".stripMargin

  /** A sink that FORWARDS a `yields`-declaring command by name. The parser grants `yieldStatement`
    * to ProcessorKind Entity/Context/Repository only, so this clause physically cannot contain the
    * yield the rule used to demand -- and `on other` is no escape, because A36 then reports an epic
    * step routed through the sink as unwitnessed.
    */
  private val sinkForwardsModel: String =
    """domain D is {
      |  context C is {
      |    event E is { data: String }
      |    command Cmd yields event E is { data: String }
      |    entity Ent is {
      |      record F is { x: Integer }
      |      state S of record Ent.F is {
      |        handler EH is {
      |          on command D.C.Cmd { yield event E(data = "the data") }
      |          on event D.C.E { set field S.x to "1" }
      |        }
      |      }
      |    }
      |    processor Intake as sink is {
      |      inlet In is command D.C.Cmd
      |      handler IH is {
      |        on command D.C.Cmd { tell command D.C.Cmd to entity Ent }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "yield conformance" should {

    "not require a yield from a streamlet clause, which cannot contain one" in { (td: TestData) =>
      val input = RiddlParserInput(sinkForwardsModel, td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val neverYields = errors(msgs).filter(_.message.contains("never yields"))
        if neverYields.nonEmpty then
          info(s"Streamlet clause was held to the yield contract:\n${neverYields.map(_.format).mkString("\n")}")
        neverYields mustBe empty
      }
    }


    "accept a yield that matches the declared yields clause" in { (td: TestData) =>
      val input = RiddlParserInput(model("""yield event E(data = "the data")"""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val conformanceErrors =
          errors(msgs).filter(m => m.message.contains("yield") || m.message.contains("yields"))
        if conformanceErrors.nonEmpty then
          info(s"Unexpected conformance errors:\n${conformanceErrors.map(_.format).mkString("\n")}")
        conformanceErrors mustBe empty
      }
    }

    "reject a yield whose message does not match the declared yields clause" in { (td: TestData) =>
      val input = RiddlParserInput(model("yield event Other"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        errors(msgs).exists(_.message.contains("does not match declared")) mustBe true
      }
    }

    "reject a handler that never yields for a yields-declaring command" in { (td: TestData) =>
      val input = RiddlParserInput(model("prompt \"do nothing\""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        // Wording moved from "never yields it" to "does not yield it on every path" when the
        // check became path-sensitive. "Never" is no longer accurate: the error now also fires
        // for a clause that yields on SOME path and falls through on another.
        errors(msgs).exists(_.message.contains("does not yield it on every path")) mustBe true
      }
    }

    // A clause that refuses discharges the contract by declining. Without these exemptions the
    // ordinary event-sourcing shape -- a command accepted in one state and refused in the others --
    // is unexpressible, because each refusing clause would have to yield the event it just refused.
    "exempt a clause that refuses with 'error' from the never-yields rule" in { (td: TestData) =>
      val input = RiddlParserInput(model("error \"cannot do that in this state\""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val neverYields = errors(msgs).filter(_.message.contains("never yields"))
        if neverYields.nonEmpty then
          info(s"Refusing clause was not exempt:\n${neverYields.map(_.format).mkString("\n")}")
        neverYields mustBe empty
      }
    }

    "exempt a clause that refuses with 'require' from the never-yields rule" in { (td: TestData) =>
      val input = RiddlParserInput(model("require \"the order is open\""), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        val neverYields = errors(msgs).filter(_.message.contains("never yields"))
        if neverYields.nonEmpty then
          info(s"Refusing clause was not exempt:\n${neverYields.map(_.format).mkString("\n")}")
        neverYields mustBe empty
      }
    }

    "still reject a refusing clause that yields the WRONG event" in { (td: TestData) =>
      // Refusing exempts only the never-yields rule. A clause that DOES yield is still held to
      // the declared type, so the exemption cannot be used to smuggle in a mismatched yield.
      val input = RiddlParserInput(model("error \"nope\"\n      yield event Other"), td)
      parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
        errors(msgs).exists(_.message.contains("does not match declared")) mustBe true
      }
    }

    "allow a yield when the command declares no yields clause (yields is optional)" in {
      (td: TestData) =>
        val input = RiddlParserInput(model("""yield event E(data = "the data")""", yieldsClause = ""), td)
        parseAndValidateInput(input, shouldFailOnErrors = false) { (_, _, msgs) =>
          // No yields declared ⇒ conformance is not enforced; the yield must not error.
          errors(msgs).exists(m => m.message.contains("yields")) mustBe false
        }
    }
  }
}
