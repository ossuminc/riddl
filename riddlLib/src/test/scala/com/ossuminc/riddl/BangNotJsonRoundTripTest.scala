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

/** Not/! synonymy task 4 (2026-08-15): `WhenStmtDto.negated` -- the JSON DTO's own wire field,
  * carried as a hardcoded `false` by task 2's minimal accommodation -- is deleted. Negation is
  * fully carried by a `NotDto`-wrapped value inside `WhenStmtDto.expression`, the same shape every
  * other `NotExpression` uses (already round-tripping since before this task).
  *
  * Modeled on `TypedHoleJsonRoundTripTest`: a `root2Json`/`parseJson` fixed point ALONE is not
  * sufficient proof the field is gone -- a consistently-dropped field is still a perfect fixed
  * point (json1 == json2 even if neither ever carried "negated"). So this file additionally
  * inspects the raw JSON text for the absence of the key, and decodes the AST back to confirm a
  * real `NotExpression` survives.
  */
class BangNotJsonRoundTripTest extends AnyWordSpec with Matchers {

  private def model(condSpelling: String): String =
    s"""domain D is {
       |  context C is {
       |    entity Order is {
       |      handler H is {
       |        on init {
       |          when $condSpelling flag then
       |            do "boom"
       |          end
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def whenConditionIn(root: Root): Value =
    Finder(root)
      .recursiveFindByType[WhenStatement]
      .headOption
      .getOrElse(fail("no WhenStatement found"))
      .condition match
      case v: Value => v
      case other    => fail(s"expected the condition to be a Value, got $other")

  "a `when not`/`when !` condition" should {

    "be a JSON-identity fixed point, for EACH spelling separately" in {
      for spelling <- Seq("not", "!") do
        RiddlLib.parseString(model(spelling)) match
          case RiddlResult.Success(root0) =>
            val json1 = RiddlLib.root2Json(root0)
            RiddlLib.parseJson(json1) match
              case RiddlResult.Success(root1) =>
                val json2 = RiddlLib.root2Json(root1)
                withClue(s"spelling '$spelling':") { json2 mustBe json1 }
              case RiddlResult.Failure(errors) =>
                fail(s"parseJson of the generated JSON failed for '$spelling': $errors")
            end match
          case RiddlResult.Failure(errors) =>
            fail(s"parse of the RIDDL model failed for '$spelling': $errors")
        end match
      end for
    }

    "produce IDENTICAL JSON for both spellings, apart from source locations" in {
      // The two spellings already build the identical AST at parse time (Task 1); this proves
      // that identity survives all the way through JSON serialization too, not merely that each
      // spelling round-trips in isolation. `$at` offsets legitimately differ -- `!flag` and
      // `not flag` are different lengths -- so they are stripped before comparing; everything
      // else, including the "expression"/NotDto payload, must be byte-identical.
      def stripLocations(v: ujson.Value): ujson.Value = v match
        case o: ujson.Obj =>
          ujson.Obj.from(o.value.view.filterKeys(_ != "$at").mapValues(stripLocations).toSeq)
        case a: ujson.Arr => ujson.Arr.from(a.value.map(stripLocations))
        case other        => other

      val notJson = RiddlLib.parseString(model("not")) match
        case RiddlResult.Success(root)   => RiddlLib.root2Json(root)
        case RiddlResult.Failure(errors) => fail(s"parse of the 'not' model failed: $errors")
      val bangJson = RiddlLib.parseString(model("!")) match
        case RiddlResult.Success(root)   => RiddlLib.root2Json(root)
        case RiddlResult.Failure(errors) => fail(s"parse of the '!' model failed: $errors")

      val notStripped = stripLocations(ujson.read(notJson))
      val bangStripped = stripLocations(ujson.read(bangJson))
      withClue(s"not:\n$notStripped\nbang:\n$bangStripped\n") {
        bangStripped mustBe notStripped
      }
    }

    "carry NO 'negated' key anywhere in the JSON, for either spelling" in {
      // A fixed-point test alone would still pass if the field were silently dropped on both
      // sides -- this inspects the raw text directly, which a dropped-field bug cannot fake.
      for spelling <- Seq("not", "!") do
        val json = RiddlLib.parseString(model(spelling)) match
          case RiddlResult.Success(root)   => RiddlLib.root2Json(root)
          case RiddlResult.Failure(errors) => fail(s"parse failed for '$spelling': $errors")
        withClue(s"spelling '$spelling', JSON was:\n$json\n") {
          json must not include "\"negated\""
        }
      end for
    }

    "decode as a real NotExpression after a JSON round trip, for either spelling" in {
      for spelling <- Seq("not", "!") do
        val decoded = RiddlLib.parseString(model(spelling)) match
          case RiddlResult.Success(root0) =>
            val json = RiddlLib.root2Json(root0)
            RiddlLib.parseJson(json) match
              case RiddlResult.Success(root1) => root1
              case RiddlResult.Failure(errors) =>
                fail(s"parseJson failed for '$spelling': $errors")
          case RiddlResult.Failure(errors) => fail(s"parse failed for '$spelling': $errors")
        withClue(s"spelling '$spelling':") {
          whenConditionIn(decoded) match
            case NotExpression(_, inner) =>
              inner match
                case vr: ValueRef => vr.path.format must be("flag")
                case other        => fail(s"expected a ValueRef('flag'), got $other")
            case other => fail(s"expected a NotExpression, got $other")
        }
      end for
    }
  }
}
