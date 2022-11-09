/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.testkit.RunCommandSpecBase
import org.scalatest.Assertion

class RiddlCommandsTest extends RunCommandSpecBase {

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  "Riddlc Commands" should {
    "generate info" in { runCommand(Array("info")) }
    "provide help" in { runCommand(Array("--quiet", "help")) }
    "print version" in { runCommand(Array("--quiet", "version")) }
    "handle parse" in {
      val args = Array("--quiet", "parse", inputFile)
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
    "handle hugo" in {
      val args = Array(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "hugo",
        inputFile,
        "-o",
        outputDir("hugo")
      )
      runCommand(args)
    }
    "handle hugo from config" in {
      val args = Array(
        "--verbose",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "from",
        hugoConfig,
        "hugo"
      )
      runCommand(args)
      // runHugo(path)
      // val root = Path.of(output).resolve(path)
      // val img = root.resolve("static/images/RBBQ.png")
      // Files.exists(img) mustBe true
    }

    "repeat validation of the ReactiveBBQ example" in {
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
  }

  def runCommand(
    args: Array[String] = Array.empty[String]
  ): Assertion = {
    val rc = CommandPlugin.runMain(args)
    rc mustBe 0
  }
}
