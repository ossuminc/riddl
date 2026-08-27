/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{Await, PathUtils, PlatformContext, URL, ec, pc}
import org.scalatest.TestData

import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.io.Source

class TopLevelParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput.*

  val origin = "simpleDomain.riddl"

  val testInput = "language/input"

  val simpleDomainFile: Path = Path.of(testInput + s"/domains/$origin")
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
      val url: URL = PathUtils.urlFromCwdPath(Path.of(testInput + "/everything.riddl"))
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
      val url: URL = PathUtils.urlFromCwdPath(Path.of(testInput + "/everything.riddl"))
      val rpi: RiddlParserInput = Await.result(RiddlParserInput.fromURL(url), 10.seconds)
      val tlp = TopLevelParser(rpi, false)
      tlp.parseRootWithURLs match {
        case Left((messages, _)) => fail(messages.format)
        case Right((root, urls)) =>
          root.domains.head.id.value must be("Everything")
          val paths: Seq[String] = urls.map(_.path)
          paths must contain(testInput + "/everything_APlant.riddl")
          paths must contain(testInput + "/everything_app.riddl")
          paths must contain(testInput + "/everything_full.riddl")
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
    // Regression: a missing closing brace at EOF previously crashed the
    // error reporter (`annotateErrorLine`) with
    // `IllegalArgumentException: requirement failed: fail: 58 >= 59`,
    // which the outer driver caught and surfaced as a SEVERE
    // "Exception Thrown" message instead of a normal Error with the
    // offending file/line. Fixture files are at
    // `language/input/riddl-bad/{badDomain,badEntity}.riddl` and
    // intentionally have 3 opens / 2 closes.
    "report a normal Error (not Severe) when closing brace is missing at EOF" in { (_: TestData) =>
      val url: URL = PathUtils.urlFromCwdPath(
        Path.of(testInput + "/riddl-bad/badDomain.riddl")
      )
      val future = TopLevelParser.parseURL(url)
      Await.result(future, 10.seconds) match
        case Right(_) =>
          fail("Expected the malformed input to fail parsing")
        case Left(messages: Messages) =>
          // No Severe / "Exception Thrown" — the error reporter
          // must not crash on the EOF boundary condition.
          val severes = messages.filter(_.kind == SevereError)
          withClue(s"Got severe messages: ${severes.map(_.format).mkString(", ")}\n") {
            severes mustBe empty
          }
          // We do expect an Error from fastparse listing `}` as one
          // of the expected tokens, located in badEntity.riddl.
          val errors = messages.filter(_.kind == Error)
          errors mustNot be(empty)
          val combined = errors.map(_.format).mkString("\n")
          combined must include("badEntity.riddl")
          combined must include("Expected")
          combined must include("}")
      end match
    }
  }
}
