/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{Await, JVMPlatformIOContext, PathUtils, PlatformIOContext}
import com.ossuminc.riddl.utils.{pc, ec}

import java.nio.file.Path
import scala.io.Source
import org.scalatest.TestData
import scala.concurrent.duration.DurationInt

class TopLevelParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val origin = "simpleDomain.riddl"

  val simpleDomainFile = Path.of(s"language/jvm/src/test/input/domains/$origin")
  val url = PathUtils.urlFromCwdPath(simpleDomainFile)
  val rpi: RiddlParserInput =
    Await.result(RiddlParserInput.fromURL(url), 10.seconds)

  val simpleDomain: AST.Domain = Domain(
    At(1, 1, rpi),
    Identifier(At(1, 8, rpi), "foo")
  )
  val simpleDomainResults: AST.Root = Root(Contents(simpleDomain))

  "parse" should {
    "parse RiddlParserInput" in { (td: TestData) =>
      TopLevelParser.parseInput(rpi) mustBe Right(simpleDomainResults)
    }
    "parse File" in { (td: TestData) =>
      TopLevelParser.parseInput(rpi) mustBe Right(simpleDomainResults)
    }
    "parse String" in { (td: TestData) =>
      val source = Source.fromFile(simpleDomainFile.toFile)
      try {
        val stringContents = source.mkString
        val result = TopLevelParser.parseInput(rpi)
        val expected = Root(Contents(simpleDomain))
        result mustBe Right(expected)
      } finally { source.close() }
    }
    "parse empty String" in { (td: TestData) =>
      val expected = Root(Contents())
      val parser = StringParser("")
      parser.parseRoot(parser.rpi) match {
        case Right(r: Root) =>
          fail(s"Should have failed expecting an author or domain but got ${r.format}")
        case Left(messages: Messages) =>
          messages.length mustBe 1
          val msg = messages.head
          msg.message must include("Expected one of")
          msg.message must include("\"author\"")
          msg.message must include("\"domain\"")
      }
    }

    "handle garbage" in { (td: TestData) =>
      val input = RiddlParserInput(" pweio afhj", td)
      TopLevelParser.parseInput(input) match {
        case Right(_) =>
          fail("Should have failed; expecting an author or domain")
        case Left(messages: Messages) =>
          messages.length mustBe 1
          val msg = messages.head
          msg.message must include("Expected one of")
          msg.message must include("\"author\"")
          msg.message must include("\"domain\"")
      }
    }
  }
}
