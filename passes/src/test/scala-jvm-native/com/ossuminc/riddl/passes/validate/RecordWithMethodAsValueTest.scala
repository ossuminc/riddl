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

/** riddl-models, 2026-11-19 (task `record-with-method-as-value-crashes`): passing a value whose type
  * is a record that declares a METHOD as a constructor argument threw `ClassCastException: Method
  * cannot be cast to Field` from `AggregateTypeExpression.isAssignmentCompatible`. Its `for`
  * comprehension wrote `myField: Field = named.asInstanceOf[Field] if named.isInstanceOf[Field]`,
  * and a value definition in a `for` is evaluated BEFORE the guard that follows it, so the guard
  * never protected the cast.
  *
  * The guard was also wrong in what it decided: had it run first, it would have DROPPED the method,
  * so a record with a method would have been judged incompatible with ITSELF. Members are compared
  * pairwise by kind -- field with field, method with method -- and a name that is a field on one
  * side and a method on the other is a mismatch, reported, not a crash.
  */
class RecordWithMethodAsValueTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def errors(msgs: Seq[Message]): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error || m.kind == Messages.SevereError)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** The task's repro, with the declared field type and the passed value's record separable:
    * `EResult.summary` is declared `<declared>` and the state field passed to it is `<passed>`.
    */
  private def model(declared: String, passed: String, extra: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    record Summary is {
       |      total: Natural
       |      count(): Natural
       |    }
       |    $extra
       |    entity E is {
       |      inlet EIn is query GetE
       |      outlet EOut is result EResult
       |      query GetE replies result EResult is { eid: EId }
       |      result EResult is { summary: $declared }
       |      type EId is Id(E)
       |      record S is { summary: $passed }
       |      state Only of record S is {
       |        handler H is {
       |          on g: query GetE is {
       |            reply result EResult(summary = E.Only.summary)
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "A record value whose type declares a method" should {
    "validate as a constructor argument of its own type, without an exception" in {
      (td: TestData) =>
        val msgs = messages(model("Summary", "Summary"), td)
        withClue(clue(msgs)) { errors(msgs) mustBe empty }
    }
    "be STRUCTURALLY compatible with an identical record, methods included" in {
      (td: TestData) =>
        // A differently NAMED record is still refused, by the nominal `ArgumentTypeMismatch`
        // rule -- record identity is by name. What this pins is that the STRUCTURAL comparison
        // does not add a `value-type-mismatch` of its own: the old guard would have dropped the
        // method and called the two shapes different.
        val twin = "record Twin is { total: Natural\n count(): Natural }"
        val msgs = messages(model("Summary", "Twin", twin), td)
        withClue(clue(msgs)) {
          msgs.filter(_.kind == Messages.SevereError) mustBe empty
          msgs.filter(_.ruleId.contains(RuleId.ArgumentTypeMismatch)) must not be empty
          msgs.filter(_.ruleId.contains(RuleId.ValueTypeMismatch)) mustBe empty
        }
    }
    "be incompatible when a same-named method returns a different type" in { (td: TestData) =>
      val other = "record Other is { total: Natural\n count(): String }"
      val msgs = messages(model("Summary", "Other", other), td)
      withClue(clue(msgs)) {
        msgs.filter(_.kind == Messages.SevereError) mustBe empty
        msgs.filter(_.ruleId.contains(RuleId.ValueTypeMismatch)) must not be empty
      }
    }
    "be incompatible when a method's arguments differ" in { (td: TestData) =>
      val other = "record Other is { total: Natural\n count(by: String): Natural }"
      val msgs = messages(model("Summary", "Other", other), td)
      withClue(clue(msgs)) {
        msgs.filter(_.kind == Messages.SevereError) mustBe empty
        msgs.filter(_.ruleId.contains(RuleId.ValueTypeMismatch)) must not be empty
      }
    }
    "be incompatible, not crash, when one side's field is the other's method" in {
      (td: TestData) =>
        val other = "record Other is { total: Natural\n count: Natural }"
        val msgs = messages(model("Summary", "Other", other), td)
        withClue(clue(msgs)) {
          msgs.filter(_.kind == Messages.SevereError) mustBe empty
          msgs.filter(_.ruleId.contains(RuleId.ValueTypeMismatch)) must not be empty
        }
    }
  }
}
