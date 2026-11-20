/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** riddl-models, 2026-11-19 (`task/done/2026-11-19-empty-guard-does-not-narrow.md`): the [1.29](a)
  * advisory `value-optional-for-required` told the author to guard with `when x != empty then`,
  * then fired INSIDE that guard. A stated guard settles the empty case, so the value is narrowed to
  * non-empty where the guard holds: the `then` of `x != empty`, the `else` of `x == empty`, each
  * conjunct of an `and`, the `else` of an `or` of `== empty` tests, through `not`, and (Reid,
  * 2026-11-20) after `require x != empty` and after an early-exit `when x == empty then error`.
  *
  * Matching is by SPELLING: `a.maybe` and `b.maybe` resolve to one Field but are two values.
  */
class EmptyGuardNarrowingTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    // A fixture that fails to PARSE would pass every "mustBe empty" vacuously.
    withClue(clue(out)) { out.exists(_.message.startsWith("Expected one of")) mustBe false }
    out

  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** The values each advisory names, in source order. */
  private def flagged(body: String, td: TestData): Seq[String] =
    val msgs = messages(model(body), td)
    msgs.filter(_.ruleId.contains(RuleId.OptionalForRequired)).map { m =>
      val i = m.message.indexOf("but '")
      m.message.drop(i + 5).takeWhile(_ != '\'')
    }

  /** `go` is the handled `Go`: `maybe`/`other: String?`, `many: String*`. State `Only` holds
    * `held: String?`. `Went` requires `surely: String`; `Bunch` requires `all: String+`.
    */
  private def model(body: String): String =
    s"""domain D is { context C is {
       |  entity E is {
       |    command Go yields event Went is { eId: Id(C.E), maybe: String?, other: String?,
       |      many: String* }
       |    event Went is { eId: Id(C.E), surely: String }
       |    event Bunch is { eId: Id(C.E), all: String+ }
       |    record EData is { eId: Id(C.E), held: String? }
       |    state Only of record E.EData
       |    handler H is {
       |      on go: command Go is {
       |$body
       |      }
       |      on went: event Went is { set field E.Only.held to went.surely }
       |    }
       |  } with { briefly "e" }
       |} }
       |""".stripMargin

  private def went(v: String): String = s"yield event Went(eId = go.eId, surely = $v)"

  "the task's repro (control: unguarded uses are flagged)" should {
    "flag a message field and a state field with no guard" in { (td: TestData) =>
      flagged(went("go.maybe") + "\n" + went("E.Only.held"), td) mustBe
        Seq("go.maybe", "E.Only.held")
    }
  }

  "a when-guard" should {
    "narrow a message field inside `when x != empty then`" in { (td: TestData) =>
      flagged(s"when go.maybe != empty then ${went("go.maybe")} end", td) mustBe empty
    }
    "narrow a state field inside `when x != empty then`" in { (td: TestData) =>
      flagged(s"when E.Only.held != empty then ${went("E.Only.held")} end", td) mustBe empty
    }
    "narrow with the operands reversed" in { (td: TestData) =>
      flagged(s"when empty != go.maybe then ${went("go.maybe")} end", td) mustBe empty
    }
    "narrow in the else of `when x == empty`" in { (td: TestData) =>
      flagged(s"""when go.maybe == empty then error "none" else ${went("go.maybe")} end""", td) mustBe
        empty
    }
    "NOT narrow in the then of `when x == empty`" in { (td: TestData) =>
      flagged(s"""when go.maybe == empty then ${went("go.maybe")} else error "x" end""", td) mustBe
        Seq("go.maybe")
    }
    "narrow both conjuncts of an `and`" in { (td: TestData) =>
      flagged(
        s"when go.maybe != empty and go.other != empty then ${went("go.maybe")} ${went("go.other")} end",
        td
      ) mustBe empty
    }
    "narrow both in the else of an `or` of empty tests" in { (td: TestData) =>
      flagged(
        s"""when go.maybe == empty or go.other == empty then error "x" else ${went("go.maybe")} ${went(
            "go.other"
          )} end""",
        td
      ) mustBe empty
    }
    "NOT narrow either side in the then of an `or`" in { (td: TestData) =>
      flagged(
        s"""when go.maybe != empty or go.other != empty then ${went("go.maybe")} else error "x" end""",
        td
      ) mustBe Seq("go.maybe")
    }
    "narrow through `not`" in { (td: TestData) =>
      flagged(s"when not (go.maybe == empty) then ${went("go.maybe")} end", td) mustBe empty
    }
    "NOT narrow a different path" in { (td: TestData) =>
      flagged(s"when go.other != empty then ${went("go.maybe")} end", td) mustBe Seq("go.maybe")
    }
    "NOT narrow after the when ends" in { (td: TestData) =>
      flagged(s"when go.maybe != empty then do \"a\" end\n${went("go.maybe")}", td) mustBe
        Seq("go.maybe")
    }
    "NOT narrow a field after it is set inside the guard" in { (td: TestData) =>
      flagged(
        s"when E.Only.held != empty then set field E.Only.held to go.other ${went("E.Only.held")} end",
        td
      ) mustBe Seq("E.Only.held")
    }
    // riddl-models' subscription-management: a morph replaces the STATE, so it may make a state
    // path empty again, but a message field or parameter cannot change.
    "keep a message-field narrowing across a morph, and drop a state one" in { (td: TestData) =>
      val morph = "morph entity E to state E.Only with record E.EData(eId = go.eId, held = go.other)"
      flagged(s"when go.maybe != empty then $morph ${went("go.maybe")} end", td) mustBe empty
      flagged(s"when E.Only.held != empty then $morph ${went("E.Only.held")} end", td) mustBe
        Seq("E.Only.held")
    }
    "narrow a zero-or-more collection given to a one-or-more field" in { (td: TestData) =>
      flagged("yield event Bunch(eId = go.eId, all = go.many)", td) mustBe Seq("go.many")
      flagged("when go.many != empty then yield event Bunch(eId = go.eId, all = go.many) end", td) mustBe
        empty
    }
  }

  "a refusal ahead of the use (Reid, 2026-11-20)" should {
    "narrow every later statement after `require x != empty`" in { (td: TestData) =>
      flagged(s"require go.maybe != empty\n${went("go.maybe")}", td) mustBe empty
    }
    "narrow every later statement after `when x == empty then error … end`" in { (td: TestData) =>
      flagged(s"""when go.maybe == empty then error "none" end\n${went("go.maybe")}""", td) mustBe
        empty
    }
    "NOT narrow when the early branch does not end the block" in { (td: TestData) =>
      flagged(s"""when go.maybe == empty then do "log it" end\n${went("go.maybe")}""", td) mustBe
        Seq("go.maybe")
    }
  }

  "a `return` value" should {
    "be narrowed by its guard too" in { (td: TestData) =>
      def fn(body: String): String =
        s"""domain D is { context C is {
           |  type Name is String
           |  record In is { maybe: String? } with { briefly "i" }
           |  function F is {
           |    requires record C.In
           |    returns type C.Name
           |$body
           |    return "x"
           |  } with { briefly "f" }
           |} }
           |""".stripMargin
      def count(body: String): Int =
        messages(fn(body), td).count(_.ruleId.contains(RuleId.OptionalForRequired))
      count("    return maybe") mustBe 1
      count("    when maybe != empty then return maybe end") mustBe 0
    }
  }
}
