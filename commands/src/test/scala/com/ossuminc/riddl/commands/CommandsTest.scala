/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

class CommandsTest extends CommandTestBase {

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"


  "Commands" should {
    "handle dump" in {
      val args = common ++ Seq("dump", inputFile)
      runCommand(args)
    }
    "handle from" in {
      FromCommand.Options().command mustBe "from"
      val args = common ++ Seq("from", validateConfig, "validate")
      runCommand(args)
    }
    "handle parse" in {
      val args = common ++ Seq("parse", inputFile)
      runCommand(args)
    }
    "handle repeat" in {
      RepeatCommand.Options().command mustBe "repeat"
      val args = common ++ Seq("repeat", validateConfig, "validate", "1s", "2")
      runCommand(args)
    }
    "handle validate" in {
      val args = common ++ Seq("validate", inputFile)
      runCommand(args)
    }
  }
}
