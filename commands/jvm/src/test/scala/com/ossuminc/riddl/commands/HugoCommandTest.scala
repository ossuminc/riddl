/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands


class HugoCommandTest extends CommandTestBase  {

  val inputFile = "passes/input/rbbq.riddl"
  val hugoConfig = "commands/input/hugo.conf"
  val validateConfig = "commands/input/hugo/validate.conf"
  val outputDir: String => String = (name: String) => s"commands/target/test/$name"

  "HugoCommand" should {
    "handle hugo" in {
      val args = Seq(
        "--quiet",
        "--hide-missing-warnings",
        "--hide-style-warnings",
        "hugo",
        inputFile,
        "-o",
        outputDir("hugo"),
        "--hugo-theme-name", "GeekDoc"
      )
      runCommand(args)
    }
    "handle hugo from config" in {
      val args = Seq(
        "--verbose",
        "--hide-missing-warnings",
        "--hide-style-warnings",
        "from",
        hugoConfig,
        "hugo"
      )
      runCommand(args)
    }
  }
}
