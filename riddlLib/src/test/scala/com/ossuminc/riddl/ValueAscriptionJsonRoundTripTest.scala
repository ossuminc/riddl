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

/** JSON keeps a value's ascription under its existing `"type"` key, and the reader DERIVES
  * `typeRef` from it (BACKLOG [1.26]).
  *
  * `typeRef` is fully determined by a name ascription, so the JSON reader builds both through the
  * same `ascribed` constructor the parser uses rather than storing the name twice. That keeps the
  * wire format unchanged -- JSON written before 2.4.0 still loads, and loads to the same AST the
  * parser builds. Pinned here: a name comes back as a `typeRef`, an expression comes back as one,
  * and the document is a JSON-identity fixed point.
  */
class ValueAscriptionJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    type MaybeNote is String?
      |    command Go is { why: String }
      |    entity E is {
      |      handler H is {
      |        on command Go is {
      |          let emptyName = empty MaybeNote
      |          let emptyExpr = empty String?
      |          let promptName = prompt("x") as MaybeNote
      |          let promptExpr = prompt("x") as String*
      |          do "done"
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def let(root: Root, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let named '$name'"))
      .expression

  private def roundTripped: (Root, String, String) =
    RiddlLib.parseString(model) match
      case RiddlResult.Success(root0) =>
        val json1 = RiddlLib.root2Json(root0)
        RiddlLib.parseJson(json1) match
          case RiddlResult.Success(root1) => (root1, json1, RiddlLib.root2Json(root1))
          case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
      case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")

  "a value's type ascription" should {
    "be a JSON-identity fixed point" in {
      val (_, json1, json2) = roundTripped
      json2 mustBe json1
    }

    "come back as a `typeRef` when it is a name" in {
      val (root, _, _) = roundTripped
      let(root, "emptyName").asInstanceOf[EmptyValue].typeRef.map(_.pathId.format) mustBe
        Some("MaybeNote")
      let(root, "promptName").asInstanceOf[PromptValue].typeRef.map(_.pathId.format) mustBe
        Some("MaybeNote")
    }

    "come back as an expression, with no `typeRef`, when it is one" in {
      val (root, _, _) = roundTripped
      val ev = let(root, "emptyExpr").asInstanceOf[EmptyValue]
      ev.typeRef mustBe None
      ev.expressionAscription.get mustBe an[Optional]
      val pv = let(root, "promptExpr").asInstanceOf[PromptValue]
      pv.typeRef mustBe None
      pv.expressionAscription.get mustBe a[ZeroOrMore]
    }
  }
}
