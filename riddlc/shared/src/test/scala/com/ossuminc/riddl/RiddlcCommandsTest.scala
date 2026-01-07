/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.commands.RunCommandSpecBase
import com.ossuminc.riddl.utils.pc
import org.scalatest.Assertion

class RiddlcCommandsTest extends RunCommandSpecBase {

  val inputFile = "commands/input/rbbq.riddl"
  val hugoConfig = "commands/input/hugo.conf"
  val validateConfig = "commands/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  "Riddlc Commands" should {
    "provide about info " in {
      val args = Seq("about")
      runWith(args)
    }
    "provide about info without ANSI coloring" in {
      val args = Seq("--no-ansi-messages", "about")
      runWith(args)
    }
    "provide help" in {
      val args = Seq("help")
      runWith(args)
    }
    "generate info" in {
      val args = Seq("info")
      runWith(args)
    }
    "print version" in {
      val args = Seq("version")
      runWith(args)
    }
  }

  override def runWith(args: Seq[String]): Assertion = {
    RIDDLC.main(args.toArray)
    succeed
  }
}
