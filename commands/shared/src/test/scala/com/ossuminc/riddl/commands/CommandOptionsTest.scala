/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommandOptions
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.AbstractTestingBasis
import org.scalatest.Assertion

import java.nio.file.{Files, Path}

class CommandOptionsTest extends AbstractTestingBasis {

  "CommandOptions" must {
    "check inputFile validity" in {
      case class FakeCommandOptions(command: String, inputFile: Option[Path], test: Boolean) extends CommandOptions
      val fco = FakeCommandOptions("doit", None, false)
      val messages = fco.check
      messages.justErrors.head.message must be("An input path was not provided.")
    }
    "show empty is empty" in {
      pending
      CommandOptions.empty.inputFile must be(empty)
      CommandOptions.empty.command must be("unspecified")
    }
    "has withInputFile handle empty inputFile" in {
      CommandOptions.withInputFile(None, "unspecified") { (path: Path) =>
        Left(List(Messages.error("Invalid path")))
      } match {
        case Left(messages) =>
          messages.size must be(1)
          messages.head.message must be("No input file specified for unspecified")
        case Right(x) => fail(s"Unexpected: Right($x)")
      }
    }
    "has withInputFile handle non-empty inputFile" in {
      CommandOptions.withInputFile[Assertion](Some(Path.of(".")), "unspecified") { (path: Path) =>
        Right(Files.exists(path) must be(true))
      } match {
        case Left(messages) => fail(s"Unexpected: ${messages.format}")
        case Right(assertion) => assertion
      }
    }
  }
}
