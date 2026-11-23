/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Validation and typing of the cardinality range `T{n,m}` (Reid, 2026-11-23): min <= max (an exact
  * count is legal), a maximum of 0 is an Error, and the existing cardinality rules -- `empty` at
  * minimum zero, optional-for-required, the collection map's typing -- now reachable from source.
  */
class RangeCardinalityTest extends AbstractValidatingTest {

  private def all(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured

  private def errors(ms: Messages): Messages = ms.filter(_.kind == Messages.Error)
  private def of(ms: Messages, rule: RuleId): Messages = ms.filter(_.ruleId.contains(rule))
  private def clue(ms: Messages): String = ms.map(_.format).mkString("\n")

  private def typeDecl(t: String): String =
    s"""domain D is { context C is {
       |  type T is $t with { briefly "t" }
       |} with { briefly "c" } } with { briefly "d" }
       |""".stripMargin

  /** `Done(f = <arg>)` with `f` declared `<fieldType>`, and the state record `E.F` in scope. */
  private def fill(fieldType: String, arg: String): String =
    s"""domain D is { context C is {
       |  command Go yields event Done is { n: Integer } with { briefly "c" }
       |  event Done is { f: $fieldType } with { briefly "e" }
       |  entity E is {
       |    record F is { few: Integer{1,3}, some: Integer{,3}, four: Integer{2,4} }
       |      with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    outlet O is event Done
       |    handler H is {
       |      on g: command Go is { yield event Done(f = $arg) }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  "a cardinality range" should {
    "accept an exact count" in { (td: TestData) =>
      val ms = all(typeDecl("Integer{3,3}"), td.name)
      withClue(clue(ms)) { errors(ms) mustBe empty }
    }
    "refuse a minimum above the maximum" in { (td: TestData) =>
      val ms = all(typeDecl("Integer{3,1}"), td.name)
      withClue(clue(ms)) { of(ms, RuleId.MinExceedsMax) must have size 1 }
    }
    "refuse a maximum of zero" in { (td: TestData) =>
      val ms = all(typeDecl("Integer{,0}"), td.name)
      withClue(clue(ms)) {
        of(ms, RuleId.RangeMaxZero) must have size 1
        of(ms, RuleId.MinExceedsMax) mustBe empty
      }
    }
    "accept open bounds on either side" in { (td: TestData) =>
      Seq("Integer{2,}", "Integer{,5}", "Integer{,}").foreach { t =>
        val ms = all(typeDecl(t), td.name)
        withClue(s"$t:\n${clue(ms)}") { errors(ms) mustBe empty }
      }
    }
  }

  "a value of range type" should {
    "fill a T+ cleanly when its minimum is at least one" in { (td: TestData) =>
      val ms = all(fill("Integer+", "E.S.few"), td.name)
      withClue(clue(ms)) {
        errors(ms) mustBe empty
        of(ms, RuleId.OptionalForRequired) mustBe empty
      }
    }
    "fill a T+ with the usual Advisory when its minimum is zero" in { (td: TestData) =>
      val ms = all(fill("Integer+", "E.S.some"), td.name)
      withClue(clue(ms)) {
        errors(ms) mustBe empty
        of(ms, RuleId.OptionalForRequired) must have size 1
      }
    }
    "admit empty only at minimum zero" in { (td: TestData) =>
      val zero = all(fill("Integer{,5}", "empty"), td.name)
      withClue(clue(zero)) { errors(zero) mustBe empty }
      val one = all(fill("Integer{1,5}", "empty"), td.name)
      withClue(clue(one)) { errors(one) must not be empty }
    }
    "keep its bounds through an unfiltered map, and lose its minimum through a filter" in {
      (td: TestData) =>
        val kept = all(fill("Integer{2,4}", "each of E.S.four as x yielding x + 1"), td.name)
        withClue(clue(kept)) {
          errors(kept) mustBe empty
          of(kept, RuleId.OptionalForRequired) mustBe empty
        }
        val filtered =
          all(fill("Integer{2,4}", "each of E.S.four as x where x > 1 yielding x"), td.name)
        withClue(clue(filtered)) {
          errors(filtered) mustBe empty
          of(filtered, RuleId.OptionalForRequired) must have size 1
        }
    }
  }
}
