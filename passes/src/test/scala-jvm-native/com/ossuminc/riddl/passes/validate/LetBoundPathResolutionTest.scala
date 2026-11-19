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

/** riddl-models, 2026-11-19: a path read THROUGH a `let` binding (`valuation.totalAssetsX.amount`)
  * validated clean, while the same misspelling through a message binding or a state path was
  * `value-ref-unresolved`. `valueRefResolves` accepted a `let`-headed path as soon as the HEAD
  * matched and never walked the tail -- the defect A25 had already fixed for `foreach` elements,
  * one arm above it. The walk is the one elements use, so a `let` and an element agree.
  *
  * Every case asserts the count of `value-ref-unresolved` exactly, and the clean cases assert zero
  * errors overall, so a fixture that failed to parse cannot pass vacuously.
  */
class LetBoundPathResolutionTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def unresolved(msgs: Seq[Message]): Seq[Message] =
    msgs.filter(_.ruleId.contains(RuleId.ValueRefUnresolved))
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** `letLines` bind locals in an on-clause; `arg` is given to `Done.a: Decimal(10,2)`. In scope: the
    * state `E.S` (`v: C.Val`, `n: Integer`) and the `Ask`-able query `C.GetVal` replying `C.Val`.
    */
  private def model(letLines: String, arg: String): String =
    s"""domain D is { context C is {
       |  record Money is { amount: Decimal(10,2) } with { briefly "m" }
       |  record Val is { totalAssets: C.Money } with { briefly "v" }
       |  command Go is { n: Integer } with { briefly "g" }
       |  event Done is { a: Decimal(10,2) } with { briefly "d" }
       |  entity E is {
       |    record F is { v: C.Val, n: Integer } with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    outlet O is event Done
       |    handler H is {
       |      on command Go is {
       |        $letLines
       |        yield event Done(a = $arg)
       |      }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  private def mustReport(letLines: String, arg: String, td: TestData): Unit =
    val msgs = messages(model(letLines, arg), td)
    withClue(clue(msgs)) {
      val u = unresolved(msgs)
      u.size mustBe 1
      u.head.message must include(arg)
    }

  private def mustBeClean(letLines: String, arg: String, td: TestData): Unit =
    val msgs = messages(model(letLines, arg), td)
    withClue(clue(msgs)) { msgs.filter(_.kind == Messages.Error) mustBe empty }

  "A path through a let binding" should {
    "be reported when a field is misspelled, with a declared type" in { (td: TestData) =>
      mustReport("let t: type C.Val = E.S.v", "t.totalAssetsX.amount", td)
    }
    "be reported when a field is misspelled, with an inferred type" in { (td: TestData) =>
      mustReport("let t = E.S.v", "t.totalAssetsX.amount", td)
    }
    "be reported when a deeper field is misspelled" in { (td: TestData) =>
      mustReport("let t: type C.Val = E.S.v", "t.totalAssets.amountX", td)
    }
    "be reported through a let bound to another let" in { (td: TestData) =>
      mustReport("let t = E.S.v\n        let u = t.totalAssets", "u.amountX", td)
    }
    "be reported when the path descends into a scalar" in { (td: TestData) =>
      mustReport("let k = E.S.n", "k.amount", td)
    }
    "resolve when every field exists" in { (td: TestData) =>
      mustBeClean("let t: type C.Val = E.S.v", "t.totalAssets.amount", td)
      mustBeClean("let t = E.S.v", "t.totalAssets.amount", td)
      mustBeClean("let t = E.S.v\n        let u = t.totalAssets", "u.amount", td)
    }
    "stay silent when the let's own type cannot be determined" in { (td: TestData) =>
      // The bound value is prose: nothing is knowable about its fields, so the path is not blamed.
      val msgs = messages(model("let t = prompt(\"some valuation\")", "t.anything.amount"), td)
      withClue(clue(msgs)) { unresolved(msgs) mustBe empty }
    }
  }
}
