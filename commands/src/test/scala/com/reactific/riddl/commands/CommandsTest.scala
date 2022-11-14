/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CommandsTest extends AnyWordSpec with Matchers {

  def runCommand(
    args: Seq[String] = Seq.empty[String]
  ): Assertion = {
    val rc = CommandPlugin.runMain(args.toArray)
    rc mustBe 0
  }

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val quiet = "--quiet"
  val suppressMissing = "--suppress-missing-warnings"
  val suppressStyle = "--suppress-style-warnings"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  val common = Seq(quiet, suppressMissing, suppressStyle)

  "Commands" should {
    "handle dump" in {
      val args = common ++ Seq("dump", inputFile)
      runCommand(args)
    }
    "handle from" in {
      val args = common ++ Seq("from", validateConfig, "validate")
      runCommand(args)
    }
    "handle parse" in {
      val args = common ++ Seq("parse", inputFile)
      runCommand(args)
    }
    "handle repeat" in {
      val args = common ++
        Seq("repeat", validateConfig, "validate", "1s", "2")
      runCommand(args)
    }
    "handle stats" in {
      val args = common ++ Seq("stats", inputFile)
      runCommand(args)
    }
    "handle validate" in {
      val args = common ++ Seq("validate", inputFile)
      runCommand(args)
    }
  }
}
