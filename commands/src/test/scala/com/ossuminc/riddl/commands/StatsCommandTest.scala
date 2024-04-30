package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommandTestBase

import java.nio.file.Path

/** Unit Tests For StatsCommandTest */
class StatsCommandTest extends CommandTestBase("commands/src/test/input") {

  val inputFile = "testkit/src/test/input/rbbq.riddl"

  "StatsCommand" should {
    "run correctly" in {
      val args = common ++ Seq("stats", "--input-file", inputFile)
      runCommand(args)
    }

    "read stats option" in {
      val expected = StatsCommand.Options(Some(Path.of(s"$inputDir/stats.riddl")))
      check(new StatsCommand, expected)
    }
  }
}
