package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.stats.StatsPass
import com.ossuminc.riddl.utils.{Await, URL}
import org.scalatest.TestData

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

class RunPassTestTest extends RunPassTest {
  "RunPassTestTest" should {
    "work for stats pass" in { (td: TestData) =>
        val url = URL("file","", "", "passes/jvm/src/test/input/rbbq.riddl")
        val inputFuture = RiddlParserInput.fromURL(url, td)
        inputFuture.map { input =>
          val result = runPassesWith(input, StatsPass.creator())
          if result.messages.hasErrors then
            fail(result.messages.justErrors.format)
          else
            succeed
        }
        Await.result(inputFuture, 10)
      }
  }
}
