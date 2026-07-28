/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc

import org.scalatest.{Assertion, TestData}

/** Being persisted by a repository's schema is a use of a type.
  *
  * A schema names the types it stores with `of <name> as type <T>`. Those references resolve — the
  * model reports no error — but they never reached `associateUsage`, so the types were reported
  * unused. Referencing the same type from a field or a state DID count, which made the warning
  * incoherent: the only way to "use" a persisted record was to invent a field or state for it,
  * adding fiction to a model purely to satisfy a check.
  *
  * The cause is the shape this repository keeps rediscovering: a `Schema` is a `Leaf` whose
  * references live in ordinary FIELDS (`data`, `links`, `indices`) rather than in `contents`, and
  * the resolver only walks `contents`. `Schema` had no case of its own and fell through to the
  * catch-all `case _: Definition => ()`.
  */
class SchemaUsageTest extends AbstractValidatingTest {

  private def validating(input: String, td: TestData)(check: Messages.Messages => Assertion) =
    parseAndValidateInput(RiddlParserInput(input, td), shouldFailOnErrors = false) {
      case (_, _, msgs: Messages.Messages) => check(msgs)
    }

  /** `Stored` is referenced ONLY by the schema — no field, no state, nothing else. */
  private val onlyUsedBySchema =
    """domain D is {
      |  context C is {
      |    type Stored is { id: String }
      |    repository Store is {
      |      schema Data is relational of rows as type D.C.Stored
      |      handler H is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  "a type stored by a schema" should {

    "not be reported unused" in { (td: TestData) =>
      validating(onlyUsedBySchema, td) { msgs =>
        val unused = msgs.filter(m => m.message.contains("Stored") && m.message.contains("unused"))
        withClue(s"messages were:\n${msgs.format}\n") { unused mustBe empty }
      }
    }

    "still resolve without error" in { (td: TestData) =>
      validating(onlyUsedBySchema, td) { msgs =>
        withClue(s"messages were:\n${msgs.format}\n") { msgs.justErrors mustBe empty }
      }
    }
  }

  /** `of <name> as type <T>` says `T` is a TYPE. A path that lands on an entity is a semantic error
    * even though it parses — the syntax made a claim the model does not honour. This went unnoticed
    * because schema references were never resolved at all, so models came to rely on a check that
    * never ran.
    */
  private val storesAnEntity =
    """domain D is {
      |  context C is {
      |    type Datum is { id: String }
      |    entity Order is {
      |      handler H is { ??? }
      |    }
      |    repository Store is {
      |      schema Data is relational of orders as type D.C.Order
      |      handler SH is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  "a schema naming an entity where a type is required" should {
    "be an error" in { (td: TestData) =>
      validating(storesAnEntity, td) { msgs =>
        val mismatch = msgs.justErrors.filter(m =>
          m.message.contains("Order") && m.message.contains("Type was expected")
        )
        withClue(s"messages were:\n${msgs.format}\n") { mismatch must not be empty }
      }
    }
  }
}
