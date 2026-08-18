/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.pc
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** A20 typed holes, Task 4: `PromptValueDto` gains an optional `typeEx: Option[TypeExprDto]`
  * (`JsonModel.scala`), and `JsonifierPass.serializeValue` / `JsonAstBuilder.buildValue` carry it
  * through -- previously it was DROPPED SILENTLY, so a typed hole round-tripped to an untyped one.
  *
  * The `root2Json`/`parseJson` identity fixed point (as in `JsonRoundTripTest`) is NOT sufficient
  * here: a consistently-dropped field is still a perfect fixed point (json1 == json2 even when
  * neither carries `type`). So this file additionally asserts, explicitly, that the round-tripped
  * AST carries the RIGHT `TypeExpression` for an ascribed prompt and `None` for an unascribed one,
  * and that the raw JSON text actually contains a `"type"` key for the ascribed one.
  */
class TypedHoleJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    command Add is { sku: String }
      |    entity E is {
      |      record Data is { note: String }
      |      state S of record Data
      |      handler H is {
      |        on command Add is {
      |          let plain = prompt("x")
      |          let typed = prompt("x") as OrderId
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def letExpression(root: Root, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let statement named '$name' found"))
      .expression

  "a typed hole (prompt(...) as <type>)" should {

    "be a JSON-identity fixed point" in {
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

    "carry the `type` key on the ascribed prompt object ONLY, not on the unascribed one" in {
      // A generic `json must include("\"type\"")` would pass even with the field dropped -- lots of
      // OTHER constructs (fields, states, constants...) legitimately have a "type" key. So parse the
      // raw JSON with ujson and inspect the two `"value": "prompt"` objects directly: source order
      // is preserved, so the first (`plain`) must lack `type` and the second (`typed`) must carry it
      // with the OrderId alias, not merely a truthy "something is there".
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json = RiddlLib.root2Json(root0)
          val parsed = ujson.read(json)
          val promptObjs = scala.collection.mutable.ListBuffer.empty[ujson.Obj]
          def walk(v: ujson.Value): Unit = v match
            case o: ujson.Obj =>
              if o.value.get("value").exists(_.strOpt.contains("prompt")) then promptObjs += o
              o.value.values.foreach(walk)
            case a: ujson.Arr => a.value.foreach(walk)
            case _            => ()
          walk(parsed)
          promptObjs.size mustBe 2
          promptObjs(0).value.get("type") mustBe None
          val typeObj =
            promptObjs(1).value.getOrElse("type", fail("ascribed prompt has no 'type' key"))
          typeObj.obj("kind").str mustBe "Alias"
          typeObj.obj("ref").str must endWith("OrderId")
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "decode typeEx as None for the unascribed prompt and as the SAME AliasedTypeExpression for the ascribed one" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json) match
            case RiddlResult.Success(root1) =>
              letExpression(root1, "plain") match
                case pv: PromptValue => pv.typeEx mustBe None
                case other           => fail(s"expected a PromptValue, got $other")
              letExpression(root1, "typed") match
                case pv: PromptValue =>
                  pv.typeEx.getOrElse(fail("typeEx was None")) match
                    case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
                    case other => fail(s"expected an AliasedTypeExpression, got $other")
                case other => fail(s"expected a PromptValue, got $other")
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the generated JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }
  }
}
