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

/** Recurrence (2026-11-19) must survive AST -> JSON -> AST. A recurring send carries optional
  * `"every"` and `"starting"` keys beside `"at"`; an `on every` clause is `kind: "every"` with a
  * `"period"`. Both codecs are partly hand-written, which is how a new field gets silently dropped,
  * so the rebuilt values are asserted, not only the fixed point.
  */
class RecurrenceJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command Tick is { at: TimeStamp }
      |    constant Monthly: Duration = 30 days
      |    constant First: TimeStamp = "2026-12-01T00:00:00Z"
      |    entity E is {
      |      outlet O is command Tick
      |      handler H is {
      |        on init is {
      |          send command Tick(at = system.now) to outlet E.O every 30 days
      |          send command Tick(at = system.now) to outlet E.O every Monthly starting at First
      |          send command Tick(at = system.now) to outlet E.O at First
      |        }
      |        on every 30 days is { do "tick" }
      |        on every Monthly is { do "tock" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def root0: Root = RiddlLib.parseString(model) match
    case RiddlResult.Success(r)      => r
    case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")

  "recurrence JSON round-trip" should {
    "be a JSON-identity fixed point" in {
      val json1 = RiddlLib.root2Json(root0)
      RiddlLib.parseJson(json1) match
        case RiddlResult.Success(root1)  => RiddlLib.root2Json(root1) mustBe json1
        case RiddlResult.Failure(errors) => fail(s"parseJson of the generated JSON failed: $errors")
    }
    "rebuild every period, start and clause" in {
      RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
        case RiddlResult.Success(root1) =>
          val f = Finder(root1)
          f.recursiveFindByType[SendStatement].map(s => (s.at.map(_.format), s.every.map(_.format))) mustBe Seq(
            (None, Some("every 30 days")),
            (None, Some("every Monthly starting at First")),
            (Some("First"), None)
          )
          f.recursiveFindByType[OnEveryClause].map(c => (c.format, c.statements.map(_.format))) mustBe Seq(
            ("on every 30 days", Seq("\"tick\"")),
            ("on every Monthly", Seq("\"tock\""))
          )
        case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
    }
  }
}
