package com.reactific.riddl.language
import com.reactific.riddl.utils.StringLogger
import com.reactific.riddl.utils.SysLogger
import com.reactific.riddl.utils.StringBuildingPrintStream
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class TimerTest extends AnyWordSpec with Matchers {

  "timer" should {
    "measure the correct time" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val logger = StringLogger()
      val result = RiddlImpl.timer(clock, logger, "MyStage", show = true) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      logger.toString mustBe "[info] Stage 'MyStage': 2.000 seconds\n"
    }
    "not print anything" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = RiddlImpl
        .timer(clock, SysLogger(), "MyStage", show = false) {
          clock.updateInstant(_.plusSeconds(2))
          123
        }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }

}
