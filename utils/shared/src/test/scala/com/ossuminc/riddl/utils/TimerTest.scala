/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.time.Instant
import org.scalatest.TestData
import org.scalatest.wordspec.AnyWordSpec

class TimerTest extends TestingBasis {

  "timer" should {
    "measure the correct time" in { (td: TestData) =>
      println(td.name)
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val logger = StringLogger()
      val result = Timer.time("MyStage", show = true, logger) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

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
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }

}
