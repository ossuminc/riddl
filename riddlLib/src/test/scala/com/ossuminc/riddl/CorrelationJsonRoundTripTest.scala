/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A70 — a [[Correlation]] must survive AST -> JSON -> AST, JSON being the fourth and last of
  * RIDDL's serialization surfaces.
  *
  * The JSON-identity fixed point is the strong assertion: any field that serializes but does not
  * deserialize (or vice versa) makes the second document differ from the first. `timeout` and
  * `timeoutStatements` are the exposed pair here, since they live in FIELDS rather than in
  * `contents` and so are never carried by the generic child machinery.
  *
  * Runs on JVM, JS and Native.
  */
class CorrelationJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command Fulfillment is { customerId: String, orderId: String, paidAmount: Number }
      |    event PaymentTaken is { amount: Number }
      |    command ReportStalled is { why: String }
      |    entity Monitor is {
      |      handler H is { on command ReportStalled is { do "record it" } }
      |    }
      |    repository Store is { ??? }
      |    projector FulfillmentView is {
      |      updates repository Store
      |      correlation FulfillmentJoin by customerId, orderId yields command Fulfillment is {
      |        handler Collect is {
      |          on e: event PaymentTaken is { set field paidAmount to e.amount }
      |        }
      |      } times out after "30 days" {
      |        tell command ReportStalled to entity Monitor
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "Correlation JSON round-trip" should {

    "be a JSON-identity fixed point" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the generated JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "emit the correlation with its keys, target and timeout" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root) =>
          val json = RiddlLib.root2Json(root)
          json must include("\"$kind\": \"correlation\"")
          json must include("\"FulfillmentJoin\"")
          json must include("\"customerId\"")
          json must include("\"30 days\"")
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "rebuild keys in document order, and keep the timeout block" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
            case RiddlResult.Success(root1) =>
              val c = Finder(root1).recursiveFindByType[Correlation] match
                case Seq(one) => one
                case other    => fail(s"expected one Correlation, got ${other.size}")
              // Order, not membership: §6.5 makes identity the full tuple and forbids sorting.
              c.keys.map(_.value) mustBe Seq("customerId", "orderId")
              c.yields.pathId.value.last mustBe "Fulfillment"
              c.timeout.s mustBe "30 days"
              c.timeoutStatements.toSeq.size mustBe 1
              c.handlers.size mustBe 1
            case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }
  }
}
