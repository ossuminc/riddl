package com.ossuminc.riddl.stats

import com.ossuminc.riddl.commands.{CommandTestBase, InputFileCommandPlugin}

import java.nio.file.Path

/** Unit Tests For StatsCommandTest */
class StatsCommandTest extends CommandTestBase {

  val inputFile = "testkit/src/test/input/rbbq.riddl"

  "StatsCommand" should {
    "run correctly" in {
      val args = common ++ Seq("stats", inputFile)
      runCommand(args)
    }

    "read stats option" in {
      val expected = InputFileCommandPlugin
        .Options(Some(Path.of(s"$inputDir/stats.riddl")), "stats")
      check(new StatsCommand, expected)
    }
  }
}
