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

/** Numeric literals and the widened `Constant` across JSON, the fourth serialization surface.
  *
  * The JSON-identity fixed point is the strong assertion: any field that serializes but does not
  * deserialize (or vice versa) makes the second document differ from the first.
  *
  * The text assertions matter independently. The DTO stores the literal as a STRING, not a
  * `ujson.Num` — `ujson.Num` is a Double and would quietly turn `1.50` into `1.5`, `007` into `7`
  * and drop the precision of any large integer. A fixed-point test alone would not catch that,
  * because a consistently-mangled value is still a fixed point.
  */
// NOTE: a plain AnyWordSpec, so cases take NO `(td: TestData)` parameter. Writing one here would
// construct a Function1 and never evaluate the body — a silently passing test.
class NumericLiteralJsonRoundTripTest extends AnyWordSpec with Matchers {

  private def model(decl: String): String =
    s"""domain D is {
       |  context C is {
       |    $decl
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def roundTripped(src: String): Root =
    RiddlLib.parseString(src) match
      case RiddlResult.Success(root0) =>
        val json1 = RiddlLib.root2Json(root0)
        RiddlLib.parseJson(json1) match
          case RiddlResult.Success(root1) =>
            withClue("JSON is not an identity fixed point: ") {
              RiddlLib.root2Json(root1) mustBe json1
            }
            root1
          case RiddlResult.Failure(errors) =>
            fail(s"parseJson of the generated JSON failed: $errors")
      case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")

  private def constantValueOf(root: Root, name: String): ConstantValue =
    Finder(root)
      .recursiveFindByType[Constant]
      .find(_.id.value == name)
      .map(_.value)
      .getOrElse(fail(s"constant '$name' not found after the round trip"))

  "a numeric literal in JSON" should {

    "preserve its text exactly, in every form" in {
      for form <- Seq("5", "-1", "+3", "007", "1.50", "-0.25", "1e3", "1.5e-3", "2E+8") do
        val root = roundTripped(model(s"constant K: Real = $form"))
        constantValueOf(root, "K") match
          case nl: NumericLiteral => withClue(s"form $form: ") { nl.text mustBe form }
          case other              => fail(s"form $form decoded as ${other.getClass.getSimpleName}")
      end for
    }

    "not be degraded to a JSON number" in {
      // Explicit guard on the encoding itself: the payload must be a JSON string. If someone
      // "improves" the DTO to a ujson.Num, 1.50 becomes 1.5 and this is the case that says so.
      RiddlLib.parseString(model("constant K: Real = 1.50")) match
        case RiddlResult.Success(root) =>
          RiddlLib.root2Json(root) must include("\"1.50\"")
        case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")
    }
  }

  "a widened constant in JSON" should {

    "keep a numeric value" in {
      constantValueOf(roundTripped(model("constant K: Integer = 5")), "K") match
        case nl: NumericLiteral => nl.text mustBe "5"
        case other              => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a boolean value" in {
      constantValueOf(roundTripped(model("constant K: Boolean = true")), "K") match
        case bl: BooleanLiteral => bl.value mustBe true
        case other              => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a prompt value" in {
      val decl = """constant K: Real = prompt("the gravitational constant")"""
      constantValueOf(roundTripped(model(decl)), "K") match
        case pv: PromptValue => pv.prompt.s must include("gravitational")
        case other           => fail(s"decoded as ${other.getClass.getSimpleName}")
    }

    "keep a string value" in {
      val decl = """constant K: String = "Fred""""
      constantValueOf(roundTripped(model(decl)), "K") match
        case ls: LiteralString => ls.s mustBe "Fred"
        case other             => fail(s"decoded as ${other.getClass.getSimpleName}")
    }
  }
}
