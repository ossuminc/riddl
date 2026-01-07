/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.TestData
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class TimerTest extends AbstractTestingBasis {
  import TimerTest.*

  "timer" should {
    "measure the correct time" in { (td: TestData) =>
      println(td.name)
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val logger = StringLogger()
      val result = Timer.time("MyStage", show = true) {
        clock.updateInstant(_.plusSeconds(2))
        TEST_TIMER_RETURN_VALUE
      }

      result mustBe TEST_TIMER_RETURN_VALUE

      clock.instant() mustBe start.plusSeconds(2)
      logger.toString.matches("[info] Stage 'MyStage': 0.0\\d\\d seconds\n")
    }
    "not print anything" in { (td: TestData) =>
      println(td.name)
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = Timer.time("MyStage", show = false) {
        clock.updateInstant(_.plusSeconds(2))
        TEST_TIMER_RETURN_VALUE
      }

      result mustBe TEST_TIMER_RETURN_VALUE

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }

}

object TimerTest {
  // Test timer return value
  val TEST_TIMER_RETURN_VALUE: Int = 123
}
