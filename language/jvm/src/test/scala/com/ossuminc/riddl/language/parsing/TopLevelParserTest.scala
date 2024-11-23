/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{Await, JVMPlatformContext, PathUtils, PlatformContext, URL, ec, pc}

import java.nio.file.Path
import scala.io.Source
import org.scalatest.TestData

import scala.concurrent.duration.DurationInt

class TopLevelParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val origin = "simpleDomain.riddl"

  val simpleDomainFile: Path = Path.of(s"language/jvm/src/test/input/domains/$origin")
  val url: URL = PathUtils.urlFromCwdPath(simpleDomainFile)
  val rpi: RiddlParserInput =
    Await.result(RiddlParserInput.fromURL(url), 10.seconds)
  val location: At = At(1, 1, rpi)
  val simpleDomain: AST.Domain = Domain(location, Identifier(At(1, 8, rpi), "foo"))
  val simpleDomainResults: AST.Root = Root(location, Contents(simpleDomain))

  "TopLevelParser Companion" should {
    "parse RiddlParserInput" in { (_: TestData) =>
      TopLevelParser.parseInput(rpi) mustBe Right(simpleDomainResults)
    }

    "parse from a URL" in { (_: TestData) =>
      val url: URL = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      val future = TopLevelParser.parseURL(url)
      Await.result(future, 10.seconds) match
        case Right(r: Root) =>
          r.domains.head.id.value must be("Everything")
        case Left(messages: Messages) =>
          fail(messages.format)
      end match
    }

    "parse File" in { (_: TestData) =>
      TopLevelParser.parseInput(rpi) mustBe Right(simpleDomainResults)
    }

    "parse String" in { (_: TestData) =>
      val source = Source.fromFile(simpleDomainFile.toFile)
      try {
        val result = TopLevelParser.parseInput(rpi)
        result mustBe Right(simpleDomainResults)
      } finally { source.close() }
    }

    "parse empty String" in { (_: TestData) =>
      val parser = StringParser("")
      parser.parseRoot match {
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
  "TopLevelParser" should {
    "return URLs when asked" in { (td: TestData) =>
      val url: URL = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      val rpi: RiddlParserInput = Await.result(RiddlParserInput.fromURL(url), 10.seconds)
      val tlp = TopLevelParser(rpi, false)
      tlp.parseRootWithURLs match {
        case Left((messages, _)) => fail(messages.format)
        case Right((root, urls)) =>
          root.domains.head.id.value must be("Everything")
          val paths: Seq[String] = urls.map(_.path)
          paths must contain("language/jvm/src/test/input/everything_APlant.riddl")
          paths must contain("language/jvm/src/test/input/everything_app.riddl")
          paths must contain("language/jvm/src/test/input/everything_full.riddl")
      }
    }

    "return URLs on failure" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput("some source that ain't riddl", td)
      val tlp = TopLevelParser(rpi, false)
      tlp.parseRootWithURLs match {
        case Left((messages, urls)) =>
          urls must be(empty)
          messages mustNot be(empty)
          succeed
        case Right((root, urls)) => fail("Test should have yields Left")
      }
    }
    "parse nebulous content" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput(
        """constant bar is String = "nothing"
          |type foo is Integer
          |entity foobar is { ??? }
          |""".stripMargin,
        td
      )

      val tlp = TopLevelParser(rpi, false)
      tlp.parseNebula match {
        case Left(messages) => fail(messages.format)
        case Right(result)  => result.contents.length must be(3)
      }
    }

    "parse nebulous content with urls" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput(
        """constant bar is String = "nothing"
          |type foo is Integer
          |entity foobar is { ??? }
          |""".stripMargin,
        td
      )

      val tlp = TopLevelParser(rpi, false)
      tlp.parseNebula match {
        case Left(messages) => fail(messages.format)
        case Right(result)  => result.contents.length must be(3)
      }
    }
  }
}
