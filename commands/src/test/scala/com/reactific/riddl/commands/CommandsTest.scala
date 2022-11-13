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
    args: Array[String] = Array.empty[String]
  ): Assertion = {
    val rc = CommandPlugin.runMain(args)
    rc mustBe 0
  }

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  "Commands" should {
    "handle dump" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "dump",
        inputFile
      )
      runCommand(args)
    }
    "handle from" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "from",
        validateConfig,
        "validate"
      )
      runCommand(args)
    }
    "handle parse" in {
      val args = Array("--quiet", "parse", inputFile)
      runCommand(args)
    }
    "handle repeat" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "repeat",
        validateConfig,
        "validate",
        "1s",
        "2"
      )
      runCommand(args)
    }
    "handle stats" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "stats",
        inputFile
      )
      runCommand(args)
    }
    "handle validate" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "validate",
        inputFile
      )
      runCommand(args)
    }
  }
}
