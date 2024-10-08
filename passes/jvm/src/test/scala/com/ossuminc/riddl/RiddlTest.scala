/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.*
import com.ossuminc.riddl.passes.{PassesResult, Riddl}

import org.scalatest.{TestData,Assertion}
import java.nio.file.Path
import java.util.UUID
import scala.io.Source

class RiddlTest extends ParsingTest {

  "parse" should {
    "parse a file" in { (td:TestData) =>
      val rpi = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/rbbq.riddl"), td)
      TopLevelParser.parseInput(rpi, commonOptions = CommonOptions(showTimes = true)
      ) match {
        case Left(errors) => fail(errors.format)
        case Right(_)     => succeed
      }
    }

    "return an error when file does not exist" in { (td:TestData) =>
      val options = CommonOptions(showTimes = true)
      val path = Path.of(UUID.randomUUID().toString)
      val xcptn = intercept[java.io.FileNotFoundException] {
        RiddlParserInput.fromCwdPath(path, td)
      }
    }
    "record errors" in { (td:TestData) =>
      val riddlParserInput: RiddlParserInput =
        RiddlParserInput(UUID.randomUUID().toString, td)
      val options = CommonOptions(showTimes = true)
      TopLevelParser.parseInput(input = riddlParserInput, options) match {
        case Right(_) => succeed
        case Left(errors) if errors.nonEmpty =>
          require(
            errors.exists(_.kind == Messages.Error),
            "When failing to parse, an Error message should be logged."
          )
        case Left(errors) => fail(errors.mkString(System.lineSeparator()))
      }
    }
  }

  "parseAndValidate" should {
    def runOne(pathname: String): Either[Messages, PassesResult] = {
      val common = CommonOptions(showTimes = true)
      val rpi = RiddlParserInput.fromCwdPath(Path.of(pathname))
      Riddl.parseAndValidate(rpi, common)
    }

    "parse and validate a simple domain from path" in { (td:TestData) =>
      runOne("language/jvm/src/test/input/domains/simpleDomain.riddl") match {
        case Right(_: PassesResult) => succeed
        case Left(errors) =>
          errors.forall(_.kind != Messages.Error) must be(true)
      }
    }

    "parse and validate nonsense file as invalid" in { (td:TestData) =>
      runOne("language/jvm/src/test/input/invalid.riddl") match {
        case Right(root) => fail(s"Should not have parsed, but got:\n$root")
        case Left(errors) => assert(errors.exists(_.kind == Messages.Error))
      }
    }

    "parse and validate a simple domain from input" in { (td:TestData) =>
      val content: String = {
        val source = Source.fromFile(Path.of("language/jvm//src/test/input/domains/simpleDomain.riddl").toFile)
        try source.mkString
        finally source.close()
      }
      val common = CommonOptions(showTimes = true)
      val input = RiddlParserInput(content, td)
      Riddl.parseAndValidate(input, common) match {
        case Right(_: PassesResult) => succeed
        case Left(messages) =>
          messages.forall(_.kind != Messages.Error) must be(true)
      }
    }

    "parse and validate nonsense input as invalid" in { (td:TestData) =>
      val common = CommonOptions(showTimes = true)
      val input = RiddlParserInput("I am not valid riddl (hopefully).", td)
      Riddl.parseAndValidate(input, common) match {
        case Right(_) => succeed
        case Left(messages) => assert(messages.exists(_.kind == Messages.Error))
      }
    }
  }
}
