/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.CommandTestBase

import java.nio.file.Path

/** Unit Tests For StatsCommandTest */
class StatsCommandTest extends CommandTestBase("commands/input") {

  val inputFile = "commands/input/rbbq.riddl"

  "StatsCommand" should {
    "run correctly" in {
      val args = common ++ Seq("stats", "--input-file", inputFile)
      runCommand(args)
    }

    "read stats option" in {
      val expected = StatsCommand.Options(Some(Path.of(s"stats.riddl")))
      check(new StatsCommand, expected)
    }

    // I1: parse-time messages (e.g. the deprecated `flow` shape keyword) must surface
    // under PassCommand paths like `stats`, not only under `validate`.
    "surface a parse-time Deprecation under the stats (PassCommand) path" in {
      val args = common ++ Seq("stats", "--input-file", "commands/input/deprecated-flow.riddl")
      Commands.runMainForTest(args.toArray) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result) =>
          val deprecations = result.messages.justDeprecations
          info(deprecations.format)
          deprecations.exists { m =>
            m.message.contains("flow") && m.message.contains("streamlet")
          } must be(true)
      end match
    }
  }
}
