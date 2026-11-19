/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** BACKLOG [1.27] step 1 (Q-A, ruled "chain" by Reid 2026-11-19): a VALUE path may pass THROUGH an
  * optional field, and the value it reaches is then itself optional. `m.amount` where `m: Money?`
  * has type `Integer?`, so it fills an `Integer?` field in silence and an `Integer` field with the
  * optional-for-required Advisory.
  *
  * Only `?` chains. A collection (`*`, `+`, `{n,m}`) stays unwalkable, because a field of a
  * collection is a collection, which is `map`, and the CM declines `map`. And only VALUE paths
  * chain: a `foreach … in field` path through `?` is still refused (`CardinalityPathDescentTest`).
  *
  * Every silent case asserts the WHOLE model is error-free, so a fixture that failed to parse could
  * not pass vacuously.
  */
class OptionalChainingTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** `Go` yields `Done(f = <arg>)`, `f` declared `<fieldType>`. In scope: `g` (`mm: C.Money?`,
    * `n: Integer`) and the state `E.S` (`maybe: C.Money?`, `aliased: C.MaybeMoney`, `many:
    * C.Money*`, `sure: C.Money`).
    */
  private def model(fieldType: String, arg: String, letLine: String = ""): String =
    s"""domain D is { context C is {
       |  record Money is { amount: Integer, currency: String } with { briefly "m" }
       |  type MaybeMoney is C.Money?
       |  command Go yields event Done is { mm: C.Money?, n: Integer } with { briefly "c" }
       |  event Done is { f: $fieldType } with { briefly "e" }
       |  entity E is {
       |    record F is { maybe: C.Money?, aliased: C.MaybeMoney, many: C.Money*, sure: C.Money }
       |      with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    outlet O is event Done
       |    handler H is {
       |      on g: command Go is {
       |        $letLine
       |        yield event Done(f = $arg)
       |      }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  private def mustBeClean(fieldType: String, arg: String, td: TestData, letLine: String = ""): Unit =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired) mustBe empty
    }

  private def mustAdvise(fieldType: String, arg: String, td: TestData, letLine: String = ""): Unit =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired).size mustBe 1
    }

  "A value path through an optional field" should {
    "resolve, from the state, a message binding and a let" in { (td: TestData) =>
      mustBeClean("Integer?", "E.S.maybe.amount", td)
      mustBeClean("Integer?", "g.mm.amount", td)
      mustBeClean("Integer?", "m.amount", td, "let m = E.S.maybe")
    }
    "resolve through a type NAME for an optional" in { (td: TestData) =>
      mustBeClean("Integer?", "E.S.aliased.amount", td)
    }
    "make the reached value optional, so a required field draws the Advisory" in {
      (td: TestData) =>
        mustAdvise("Integer", "E.S.maybe.amount", td)
        mustAdvise("Integer", "g.mm.amount", td)
        mustAdvise("Integer", "m.amount", td, "let m = E.S.maybe")
        mustAdvise("Integer", "E.S.aliased.amount", td)
    }
    "leave a path through a required field required" in { (td: TestData) =>
      mustBeClean("Integer", "E.S.sure.amount", td)
    }
    "still refuse a name the optional's type does not have" in { (td: TestData) =>
      val msgs = messages(model("Integer?", "E.S.maybe.nosuch"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
      val viaLet = messages(model("Integer?", "m.nosuch", "let m = E.S.maybe"), td)
      withClue(clue(viaLet)) { errors(viaLet) must not be empty }
    }
  }

  "A value path through a collection" should {
    "stay unresolved, because a field of a collection is a collection" in { (td: TestData) =>
      val msgs = messages(model("Integer?", "E.S.many.amount"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
    }
  }
}
