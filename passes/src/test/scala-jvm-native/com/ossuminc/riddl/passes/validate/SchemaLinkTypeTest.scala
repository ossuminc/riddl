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

/** `repo-link-type-mismatch` could never fire (ossum.tech, 2026-11-17): ResolutionPass never
  * resolved a schema link's two field references, so the check found neither end and fell through
  * in silence. Resolving them also exposed the check's `!=`, which compares locations and would
  * have called two `String` fields on different lines a mismatch.
  */
class SchemaLinkTypeTest extends AbstractValidatingTest {

  private def model(link: String, extra: String = ""): String =
    s"""domain Dom is { context C1 is {
       |  record R is { id: String, n: Integer, w: Whole, ref: Id(C1.E) }
       |  record R2 is { id: String, x: Integer, other: Id(C1.F) $extra }
       |  type Alias is String
       |  record R3 is { a: C1.Alias, ref: Id(C1.E) }
       |  entity E is { ??? }
       |  entity F is { ??? }
       |  repository Bare is {
       |    schema S11 is relational of d as record R of e as record R2 of f as record R3
       |      $link
       |  }
       |} }
       |""".stripMargin

  private def messages(src: String, td: TestData): Seq[Messages.Message] =
    Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
      case Right(result) => result.messages
      case Left(msgs)    => msgs

  private def mismatches(src: String, td: TestData): Seq[Messages.Message] =
    messages(src, td).filter(_.ruleId.contains(RuleId.LinkTypeMismatch))

  "A schema link" should {
    "report fields of incompatible types (the ossum.tech repro)" in { (td: TestData) =>
      val errs = mismatches(model("link m as field R.n to field R2.id"), td)
      errs.size mustBe 1
      errs.head.kind mustBe Messages.Error
      errs.head.message must include("R.n is Integer")
      errs.head.message must include("R2.id is String")
    }

    "accept fields of the same type declared in different places" in { (td: TestData) =>
      mismatches(model("link m as field R.id to field R2.id"), td) mustBe empty
    }

    "see through an alias and accept numeric kin" in { (td: TestData) =>
      mismatches(model("link m as field R3.a to field R2.id"), td) mustBe empty
      mismatches(model("link m as field R.w to field R2.x"), td) mustBe empty
    }

    "require two ids to name the same processor" in { (td: TestData) =>
      mismatches(model("link m as field R.ref to field R3.ref"), td) mustBe empty
      mismatches(model("link m as field R.ref to field R2.other"), td).size mustBe 1
    }

    "report a link naming a field that does not exist" in { (td: TestData) =>
      val msgs = messages(model("link m as field R.nope to field R2.id"), td)
      msgs.filter(m => m.kind == Messages.Error && m.message.contains("R.nope")) must not be empty
      msgs.filter(_.ruleId.contains(RuleId.LinkTypeMismatch)) mustBe empty
    }
  }
}
