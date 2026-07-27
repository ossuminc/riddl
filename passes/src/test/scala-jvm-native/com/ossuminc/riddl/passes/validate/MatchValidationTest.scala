/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A29: structured `match` validation — type-case compatibility against a closed enumeration,
  * comparison-pattern type compatibility, exhaustiveness (StyleWarning, closed subjects only),
  * guard resolution, and legacy string-match back-compat.
  */
class MatchValidationTest extends AbstractValidatingTest {

  // An entity handling `command Track { status: OrderStatus, count: Integer }` whose state carries
  // the same fields plus a String constant and a numeric constant — enough scope to exercise every
  // match form on a closed enumeration subject and a numeric subject.
  private def matchEnt(body: String): String =
    s"""domain d is {
       |  context c is {
       |    type OrderStatus is any of { Pending, Shipped, Delivered }
       |    type OtherStatus is any of { Archived }
       |    command Track is { status: OrderStatus, count: Integer }
       |    constant MaxRetries is Integer = "3"
       |    constant Label is String = "x"
       |    entity E is {
       |      record Data is { status: OrderStatus, count: Integer, active: Boolean }
       |      state S of record Data
       |      handler h is {
       |        on command Track {
       |          $body
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "A29 match validation" should {

    "accept an exhaustive type-case match over a closed enumeration (no default)" in {
      (td: TestData) =>
        parseAndValidate(
          matchEnt(
            """match status {
              |  case Pending { error "p" }
              |  case Shipped { error "s" }
              |  case Delivered { error "d" }
              |}""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          msgs.filter(m => m.message.contains("not exhaustive")) mustBe empty
          msgs.filter(m => m.kind == Error && m.message.contains("is not a member of")) mustBe empty
        }
    }

    "warn (StyleWarning) when a closed enumeration match omits an alternant and has no default" in {
      (td: TestData) =>
        parseAndValidate(
          matchEnt(
            """match status {
              |  case Pending { error "p" }
              |  case Shipped { error "s" }
              |}""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          val exhaustive =
            msgs.filter(m => m.kind == StyleWarning && m.message.contains("not exhaustive"))
          exhaustive must not be empty
          exhaustive.head.message must include("Delivered")
        }
    }

    "not warn about exhaustiveness when a default branch is present" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match status {
            |  case Pending { error "p" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.message.contains("not exhaustive")) mustBe empty
      }
    }

    "reject an unknown type-case name (resolves to nothing)" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match status {
            |  case Bogus { error "b" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Unknown type-case 'Bogus'")
      }
    }

    "reject a type-case that resolves but is not a member of the subject's enumeration" in {
      (td: TestData) =>
        // `Archived` is a real enumerator of OtherStatus — it resolves, but it is not a member of
        // the subject's OrderStatus enumeration (identity membership rejects the foreign same-kind).
        parseAndValidate(
          matchEnt(
            """match status {
              |  case Archived { error "a" }
              |  default { error "d" }
              |}""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          assertValidationMessage(msgs, Error, "is not a member of")
        }
    }

    "reject an unknown type-case name on a non-closed (numeric) subject" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match count {
            |  case Bogus { error "b" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Unknown type-case 'Bogus'")
      }
    }

    "accept a type-case naming a real definition on a non-closed subject" in { (td: TestData) =>
      // On a non-closed subject the name need only resolve to a real definition — no membership.
      parseAndValidate(
        matchEnt(
          """match count {
            |  case OrderStatus { error "o" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error && m.message.contains("type-case")) mustBe empty
      }
    }

    "accept a bare boolean value-reference guard (`case X when active`)" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match status {
            |  case Pending when active { error "p" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error) mustBe empty
      }
    }

    "reject a non-boolean bare-reference guard" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match status {
            |  case Pending when count { error "p" }
            |  default { error "d" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "must be a Boolean value")
      }
    }

    "reject a comparison pattern whose comparand type is incompatible with the subject" in {
      (td: TestData) =>
        parseAndValidate(
          matchEnt(
            """match count {
              |  case == Label { error "l" }
              |  default { error "d" }
              |}""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          assertValidationMessage(msgs, Error, "Cannot compare a numeric subject to a string value")
        }
    }

    "accept comparison patterns and a `when` guard that resolve and type-check" in {
      (td: TestData) =>
        parseAndValidate(
          matchEnt(
            """match count {
              |  case == MaxRetries { error "m" }
              |  case > MaxRetries when count > MaxRetries { error "r" }
              |  default { error "d" }
              |}""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          msgs.filter(m => m.kind == Error) mustBe empty
        }
    }

    "still accept the legacy string match unchanged (regression)" in { (td: TestData) =>
      parseAndValidate(
        matchEnt(
          """match "orderStatus" {
            |  case "pending" { error "p" }
            |  default { error "u" }
            |}""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error) mustBe empty
      }
    }
  }
}
