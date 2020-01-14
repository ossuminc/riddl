package com.yoppworks.ossum.riddl.language

import java.time.Instant

import com.yoppworks.test.AdjustableClock
import com.yoppworks.test.StringBuildingPrintStream
import java.io.File
import java.util.UUID

import com.yoppworks.test.InMemoryLogger
import com.yoppworks.test.Logging.Lvl

class RiddlTest extends ParsingTestBase {

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

  "parse" should {
    "parse a file" in {
      val logger = new InMemoryLogger
      val result = Riddl.parse(
        path = new File("language/src/test/input/rbbq.riddl").toPath,
        logger = logger,
        options = RiddlTest.DefaultOptions
      )

      result must matchPattern { case Some(_) => }
    }
    "return none when file does not exist" in {
      val logger = new InMemoryLogger
      val result = Riddl.parse(
        path = new File(UUID.randomUUID().toString).toPath,
        logger = logger,
        options = RiddlTest.DefaultOptions
      )
      result mustBe None
    }
    "record errors" in {
      val logger = new InMemoryLogger
      val riddlParserInput: RiddlParserInput =
        RiddlParserInput(UUID.randomUUID().toString)
      val result = Riddl.parse(
        input = riddlParserInput,
        logger = logger,
        options = RiddlTest.DefaultOptions
      )
      result mustBe None
      assert(
        logger.lines().exists(_.level == Lvl.Error),
        "When failing to parse, an error should be logged."
      )
    }
  }
}

object RiddlTest {

  val DefaultOptions: Riddl.Options = new Riddl.Options {
    override val showTimes: Boolean = true
    override val showWarnings: Boolean = true
    override val showMissingWarnings: Boolean = true
    override val showStyleWarnings: Boolean = true
  }
}
