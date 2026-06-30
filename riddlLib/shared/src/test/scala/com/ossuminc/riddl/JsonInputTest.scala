/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Phase 1 of the JSON -> RIDDL AST input method: prove one representative
  * model round-trips clean both ways (parseJson -> validateRoot and
  * parseJson -> root2RiddlSource -> validateString).
  */
class JsonInputTest extends AnyWordSpec with Matchers {

  // A representative model: a record type, a command with a defaulted String
  // field, an entity with a record-referencing state, and a handler whose
  // on-clause carries a `do` statement.
  private val model: String =
    """{
      |  "domains": [
      |    {
      |      "name": "Commerce",
      |      "brief": "online shopping",
      |      "contexts": [
      |        {
      |          "name": "Orders",
      |          "types": [
      |            {
      |              "name": "OrderInfo",
      |              "typeExpression": {
      |                "kind": "Record",
      |                "fields": [
      |                  { "name": "sku", "type": { "kind": "String" } },
      |                  { "name": "quantity", "type": { "kind": "Integer" } }
      |                ]
      |              }
      |            }
      |          ],
      |          "commands": [
      |            {
      |              "name": "PlaceOrder",
      |              "brief": "place a new order",
      |              "fields": [
      |                { "name": "sku", "type": { "kind": "String", "max": 64 } }
      |              ]
      |            }
      |          ],
      |          "entities": [
      |            {
      |              "name": "Order",
      |              "state": { "name": "current", "recordType": "OrderInfo" },
      |              "handlers": [
      |                {
      |                  "name": "Behavior",
      |                  "onClauses": [
      |                    {
      |                      "kind": "message",
      |                      "message": { "ref": "PlaceOrder", "kind": "command" },
      |                      "statements": [ "record the order details" ]
      |                    }
      |                  ]
      |                }
      |              ]
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  ]
      |}""".stripMargin

  "JSON input (Phase 1)" should {

    "parseJson builds a Root" in {
      RiddlLib.parseJson(model) match
        case RiddlResult.Success(root) =>
          root.domains.map(_.id.value) mustBe Seq("Commerce")
        case RiddlResult.Failure(errors) =>
          fail(s"parseJson failed: ${errors.map(_.format).mkString("\n")}")
      end match
    }

    "parseJson -> validateRoot has no errors" in {
      RiddlLib.parseJson(model) match
        case RiddlResult.Success(root) =>
          val vr = RiddlLib.validateRoot(root)
          withClue(vr.errors.map(_.format).mkString("\n")) {
            vr.errors mustBe empty
          }
          vr.succeeded mustBe true
        case RiddlResult.Failure(errors) =>
          fail(s"parseJson failed: ${errors.map(_.format).mkString("\n")}")
      end match
    }

    "parseJson -> root2RiddlSource -> validateString has no errors" in {
      RiddlLib.parseJson(model) match
        case RiddlResult.Success(root) =>
          val riddl = RiddlLib.root2RiddlSource(root)
          info(riddl)
          val vr = RiddlLib.validateString(riddl)
          withClue(s"RIDDL:\n$riddl\nERRORS:\n" + vr.errors.map(_.format).mkString("\n")) {
            vr.errors mustBe empty
          }
        case RiddlResult.Failure(errors) =>
          fail(s"parseJson failed: ${errors.map(_.format).mkString("\n")}")
      end match
    }

    "malformed JSON yields a clean Failure (not an exception)" in {
      RiddlLib.parseJson("{ this is not json }") match
        case RiddlResult.Success(_)      => fail("expected a Failure for malformed JSON")
        case RiddlResult.Failure(errors) => errors must not be empty
      end match
    }
  }
}
