/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class TimerTest extends AnyWordSpec with Matchers {

  "timer" should {
    "measure the correct time" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val logger = StringLogger()
      val result = Timer.time("MyStage", show = true, logger) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      logger.toString mustBe "[info] Stage 'MyStage': 0.001 seconds\n"
    }
    "not print anything" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = Timer.time( "MyStage", show = false) {
          clock.updateInstant(_.plusSeconds(2))
          123
        }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }

}
