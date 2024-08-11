/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.prettify.PrettifyPass

import java.nio.file.Path

class PrettifyCommandTest extends RunCommandSpecBase {

  "PrettifyCommand" must {
    "parse a simple command" in {
      val options = Seq(
        "prettify",
        "passes/jvm/src/test/input/everything.riddl",
        "-o",
        "prettify/target/test/"
      )
      runWith(options)
    }
    "load prettify options" in {
      val cmd = new PrettifyCommand
      val conf = Path.of("prettify/src/test/input/prettify.conf")
      val expected = PrettifyPass.Options(
        inputFile = Some(Path.of("nada.riddl")),
        outputDir = Some(Path.of("prettify/target/prettify/")),
        projectName = Some("Nada"),
        singleFile = true
      )
      cmd.loadOptionsFrom(conf) match {
        case Left(errors) => fail(errors.format)
        case Right(options) =>
          options must be(expected)
      }
    }
  }
}
