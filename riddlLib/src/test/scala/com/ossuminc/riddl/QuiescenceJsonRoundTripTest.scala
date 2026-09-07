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

/** `on quiescence <window>` must survive AST -> JSON -> AST, in both window forms. The window is a
  * FIELD (`OnClauseDto.window`), never carried by the generic child machinery, so the JSON-identity
  * fixed point is the strong assertion. Runs on JVM, JS and Native.
  */
class QuiescenceJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    constant Grace: Duration = "30 minutes"
      |    command Touch is { id: String }
      |    entity Cart is {
      |      handler H is {
      |        on command Touch is { do "touch" }
      |        on quiescence "30 minutes" is { do "literal window" }
      |      }
      |    }
      |    repository R is {
      |      handler RH is {
      |        on quiescence Grace is { do "value window" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "on quiescence JSON round-trip" should {

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

    "emit the clause kind and both windows" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root) =>
          val json = RiddlLib.root2Json(root)
          json must include("\"kind\": \"quiescence\"")
          json must include("\"30 minutes\"")
          json must include("\"Grace\"")
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "rebuild both clauses with their windows" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
            case RiddlResult.Success(root1) =>
              val clauses = Finder(root1).recursiveFindByType[OnQuiescenceClause]
              clauses.size mustBe 2
              clauses.map(_.window.format).sorted mustBe Seq("\"30 minutes\"", "Grace")
            case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")
      end match
    }
  }
}
