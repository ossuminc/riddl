/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Cross-platform JSON round-trip fidelity: parse RIDDL -> AST -> JSON (json1) -> AST -> JSON
  * (json2) and require `json1 == json2`. A stable fixed point proves the AST<->JSON mapping is
  * lossless and deterministic; any dropped or reordered construct makes the second JSON diverge.
  *
  * Runs on JVM, JS, and Native (unlike `Root2JsonCorpusTest`, which walks the `../riddl-models`
  * directory and is JVM-only). The inline model exercises the constructs the fidelity work fixed: a
  * multi-state entity with nested init handlers, entity-level messages, and a repository/projector
  * that define and reference their own messages.
  */
class JsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command PlaceOrder is { qty: Integer }
      |    event OrderPlaced is { qty: Integer }
      |    entity Order is {
      |      type OpenData is { qty: Integer }
      |      type ClosedData is { reason: String }
      |      command CancelOrder is { id: String }
      |      event OrderCancelled is { id: String }
      |      state Open of type Order.OpenData is {
      |        handler OpenInit is {
      |          on init is { set state Open to "initialize" }
      |        }
      |      }
      |      state Closed of type Order.ClosedData is {
      |        handler ClosedInit is {
      |          on init is { set state Closed to "initialize" }
      |        }
      |      }
      |      handler OrderHandler is {
      |        on command PlaceOrder is {
      |          morph entity Order to state Order.Open with command PlaceOrder
      |          tell event OrderPlaced to entity Order
      |        }
      |        on command CancelOrder is {
      |          morph entity Order to state Order.Closed with command CancelOrder
      |          tell event OrderCancelled to entity Order
      |        }
      |      }
      |    }
      |    repository Repo is {
      |      query FindById is { id: String }
      |      result Found is { qty: Integer }
      |      handler RepoHandler is {
      |        on query FindById is { ??? }
      |      }
      |    }
      |    projector Proj is {
      |      handler ProjHandler is {
      |        on event OrderPlaced is { ??? }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "root2Json/parseJson round-trip" should {

    "be a JSON-identity fixed point on a multi-state model" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              val json2 = RiddlLib.root2Json(root1)
              json2 mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the generated JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "preserve both entity states and processor-defined messages in the JSON" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json = RiddlLib.root2Json(root0)
          // Both states survive (the multi-state collapse regression guard)...
          json must include("\"Open\"")
          json must include("\"Closed\"")
          json must include("\"states\"")
          // ...and the repository's own query/result land in message arrays.
          json must include("\"FindById\"")
          json must include("\"Found\"")
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }
  }
}
