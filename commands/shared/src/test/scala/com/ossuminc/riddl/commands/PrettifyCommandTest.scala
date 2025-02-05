/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.pc

import java.nio.file.{Files, Path}

class PrettifyCommandTest extends RunCommandSpecBase {

  "PrettifyCommand" must {
    "parse a simple command" in {
      val options = Seq(
        "prettify",
        "-s", "true",
        "language/input/everything.riddl",
        "-o",
        "commands/target/test/"
      )
      runWith(options)
      Files.exists(Path.of("commands/target/test/prettify-output.riddl")) must be(true)
    }
    "load prettify options" in {
      val cmd = new PrettifyCommand
      val conf = Path.of("commands/input/prettify.conf")
      val expected = PrettifyCommand.Options(
        inputFile = Some(Path.of("nada.riddl")),
        outputDir = Some(Path.of("commands/target/prettify/")),
        projectName = Some("Nada")
      )
      cmd.loadOptionsFrom(conf) match {
        case Left(errors) => fail(errors.format)
        case Right(options) =>
          options must be(expected)
      }
    }
  }
}
