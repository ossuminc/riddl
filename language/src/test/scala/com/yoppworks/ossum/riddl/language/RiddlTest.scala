package com.yoppworks.ossum.riddl.language

import java.time.Instant

import com.yoppworks.test.AdjustableClock
import com.yoppworks.test.StringBuildingPrintStream
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

class RiddlTest extends WordSpec with MustMatchers {

  "timer" should {
    "measure the correct time" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = RiddlImpl.timer(clock, printStream, "MyStage", show = true) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe "Stage 'MyStage': 2.000 seconds\n"
    }
    "not print anything" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result =
        RiddlImpl.timer(clock, printStream, "MyStage", show = false) {
          clock.updateInstant(_.plusSeconds(2))
          123
        }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }
}
