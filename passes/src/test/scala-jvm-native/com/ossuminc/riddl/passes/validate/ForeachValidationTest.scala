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

/** A25: `foreach <element> in <collection> { … }` collection scoping/collection-type validation. */
class ForeachValidationTest extends AbstractValidatingTest {

  private def model(body: String, extra: String = ""): String =
    s"""domain d is {
       |  context c is {
       |    record Order is { id: String }
       |    type OrderList is many Order
       |    command Batch is { orders: OrderList }
       |    command Single is { count: Integer }
       |    record Other is { items: OrderList }
       |    $extra
       |    handler h is {
       |      on command Batch {
       |        $body
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def hasForeachError(msgs: Messages): Boolean =
    msgs.filter(_.kind == Error).exists(_.message.contains("'foreach'"))

  "Foreach validation (A25)" should {

    "accept a foreach over a collection field of the handled message" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in field Batch.orders { do "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }

    "reject a foreach over a non-collection field" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in field Single.count { do "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a collection type")
      }
    }

    // REVERSED 2026-08-09. This case formerly asserted an Error here, on the ground that
    // `Other.items` is not a field of the entity state, the handled message, or a function input.
    // That restriction was never a decision — it fell out of an allow-list tested by identity
    // against those three roots' DIRECT fields, which also (and more visibly) rejected the
    // perfectly ordinary `foreach line in field order.lines`.
    //
    // Cardinality is the whole question. If the path resolves and the type it lands on is a
    // collection, it iterates; where the field sits is the resolver's business and it has already
    // answered. An unresolvable path still errors — from the resolver, saying so precisely.
    "accept a foreach over any resolvable collection field, wherever it sits" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in field Other.items { do "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }

    "accept a foreach over a let-bound collection local" in { (td: TestData) =>
      parseAndValidate(
        model(
          """let batch: OrderList = "orders"
            |        foreach o in batch { do "process" }""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }

    "reject a foreach over a let-bound non-collection local" in { (td: TestData) =>
      parseAndValidate(
        model(
          """let single: Single = "x"
            |        foreach o in single { do "process" }""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a collection")
      }
    }

    "reject a foreach over an identifier that is not a local in scope" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in nothere { do "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-bound local in scope")
      }
    }

    "see an earlier let inside a nested when block (lexical scope)" in { (td: TestData) =>
      parseAndValidate(
        model(
          """let batch: OrderList = "orders"
            |        when "cond" then
            |          foreach o in batch { do "process" }
            |        end""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }

    "accept a nested foreach over the enclosing foreach element" in { (td: TestData) =>
      parseAndValidate(
        model(
          """foreach o in field Batch.orders {
            |          foreach x in o { do "process" }
            |        }""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }
  }
}
