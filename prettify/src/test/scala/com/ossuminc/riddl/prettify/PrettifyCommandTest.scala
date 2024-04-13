/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify
import com.ossuminc.riddl.testkit.RunCommandSpecBase

import java.nio.file.Path

class PrettifyCommandTest extends RunCommandSpecBase {

  "PrettifyCommand" must {
    "parse a simple command" in {
      val options = Seq(
        "prettify",
        "testkit/src/test/input/everything.riddl",
        "-o",
        "prettify/target/test/"
      )
      runWith(options)
    }
    "load prettify options" in {
      val cmd = new PrettifyCommand
      val conf = Path.of("prettify/src/test/input/prettify.conf")
      cmd.loadOptionsFrom(conf) match {
        case Left(errors) => fail(errors.format)
        case Right(options) =>
          val expected = PrettifyCommand.Options(
            inputFile = Some(Path.of("nada.riddl")),
            outputDir = Some(Path.of("prettify/target/prettify/")),
            projectName = Some("Nada")
          )
          options must be(expected)
      }
    }
  }
}
