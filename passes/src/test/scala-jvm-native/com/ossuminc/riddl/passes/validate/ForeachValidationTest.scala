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
       |    type Order is record { id: String }
       |    type OrderList is many Order
       |    type Batch is command { orders: OrderList }
       |    type Single is command { count: Integer }
       |    type Other is record { items: OrderList }
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
        model("""foreach o in field Batch.orders { prompt "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        hasForeachError(msgs) mustBe false
      }
    }

    "reject a foreach over a non-collection field" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in field Single.count { prompt "process" }"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a collection type")
      }
    }

    "reject a foreach over a field outside the entity state, message, or function input" in {
      (td: TestData) =>
        parseAndValidate(
          model("""foreach o in field Other.items { prompt "process" }"""),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            "must be a field of the enclosing entity's"
          )
        }
    }

    "accept a foreach over a let-bound collection local" in { (td: TestData) =>
      parseAndValidate(
        model(
          """let batch: OrderList = "orders"
            |        foreach o in batch { prompt "process" }""".stripMargin
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
            |        foreach o in single { prompt "process" }""".stripMargin
        ),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a collection")
      }
    }

    "reject a foreach over an identifier that is not a local in scope" in { (td: TestData) =>
      parseAndValidate(
        model("""foreach o in nothere { prompt "process" }"""),
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
            |          foreach o in batch { prompt "process" }
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
            |          foreach x in o { prompt "process" }
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
