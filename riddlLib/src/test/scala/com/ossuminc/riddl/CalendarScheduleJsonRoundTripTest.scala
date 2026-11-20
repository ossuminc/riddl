/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** BACKLOG [1.32]: calendar and cron schedules must survive AST -> JSON -> AST. The schedule DTO
  * is flat and hand-coded, which is how a field gets silently dropped, so every cadence and anchor
  * shape is asserted on the REBUILT value, not only the JSON fixed point.
  */
class CalendarScheduleJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val periods: Seq[String] = Seq(
    "month on day 1 at 09:00 in \"America/Toronto\"",
    "month on last day",
    "month on the third wednesday at 10:00",
    "3 months on day 1",
    "year on march 31 at 17:00",
    "2 years on the first monday of september",
    "day at 06:00",
    "weekday at 08:30:15",
    "monday, wednesday and friday at 07:00",
    "cron \"0 9 1 * *\" in \"UTC\"",
    "cron \"*/5 * * * *\""
  )

  private val model =
    s"""domain D is {
       |  context C is {
       |    command Tick is { n: Integer }
       |    entity E is {
       |      outlet O is command Tick
       |      handler H is {
       |        on init is {
       |          send command Tick(n = 1) to outlet E.O every month on last day starting at system.now
          let next = system.now + 1 month - 2 years
       |        }
       |${periods.map(p => s"        on every $p is { do \"x\" }").mkString("\n")}
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def root0: Root = RiddlLib.parseString(model) match
    case RiddlResult.Success(r)      => r
    case RiddlResult.Failure(errors) => fail(s"parse of the RIDDL model failed: $errors")

  "calendar schedule JSON round-trip" should {
    "be a JSON-identity fixed point" in {
      val json1 = RiddlLib.root2Json(root0)
      RiddlLib.parseJson(json1) match
        case RiddlResult.Success(root1)  => RiddlLib.root2Json(root1) mustBe json1
        case RiddlResult.Failure(errors) => fail(s"parseJson of the generated JSON failed: $errors")
    }
    "rebuild every schedule exactly" in {
      RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
        case RiddlResult.Success(root1) =>
          val f = Finder(root1)
          f.recursiveFindByType[OnEveryClause].map(_.period.format) mustBe periods
          f.recursiveFindByType[OnEveryClause].map(_.period).take(9).foreach(_ mustBe a[CalendarSchedule])
          f.recursiveFindByType[SendStatement].flatMap(_.every).map(_.format) mustBe
            Seq("every month on last day starting at system.now")
        case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
    }
    "rebuild a calendar period exactly" in {
      RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
        case RiddlResult.Success(root1) =>
          val lets = Finder(root1).recursiveFindByType[LetStatement]
          lets.map(_.expression.format) mustBe Seq("system.now + 1 month - 2 years")
          lets.head.expression match
            case ArithmeticExpression(_, _, ArithmeticExpression(_, _, _, p1), p2) =>
              p1 mustBe a[CalendarPeriodLiteral]
              p2 mustBe a[CalendarPeriodLiteral]
            case other => fail(s"unexpected shape: $other")
        case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
    }
  }
}
