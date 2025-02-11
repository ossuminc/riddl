/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{Await, PathUtils, PlatformContext, URL, ec, pc}
import org.scalatest.{Assertion, TestData}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/** Unit Tests For Includes */
class IncludeAndImportTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput.*
  "Include" should {
    "handle missing files" in { (td: TestData) =>
      val rpi = RiddlParserInput("domain foo is { include \"unexisting\" } explained as \"foo\"", td)
      parseDomainDefinition(rpi, identity) match {
        case Right(result) =>
          fail(s"Should have gotten 'FileNotFoundException' but succeeded with: ${result._1}")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle bad URL" in { (td: TestData) =>
      val rip = RiddlParserInput("include \"https://incredible.lightness.of.being:8900000/@@@\"", td)
      parseDomainDefinition(rip, identity) match {
        case Right(_) =>
          fail("Should have gotten 'port out of range' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("port out of range: 8900000"))
      }
    }
    "handle existing URI" in { (td: TestData) =>
      import com.ossuminc.riddl.utils.URL
      val url = URL.fromCwdPath(defaultInputDir + "/domains/simpleDomain.riddl")
      val future = fromURL(url, td).map { rpi =>
        parseDomainDefinition(rpi, identity) match {
          case Right(_) =>
            succeed
          case Left(errors) =>
            fail(errors.format)
        }
      }
      Await.result(future, 10.seconds)
    }
    "handle inclusions into domain" in { (td: TestData) =>
      val (root, rpi) = checkFile("Domain Includes", "includes/domainIncludes.riddl")
      val url = URL.fromCwdPath(defaultInputDir + "/includes/domainIncluded.riddl")
      val inc = StringParserInput("", url)
      root.domains mustNot be(empty)
      root.domains.head.includes mustNot be(empty)
      root.domains.head.includes.head.contents.isEmpty mustNot be(true)
      val actual = root.domains.head.includes.head.contents.head
      val expected = Type(
        (1, 1, inc),
        Identifier((1, 6, inc), "foo"),
        String_((1, 13, inc)),
        Contents.empty()
      )
      actual mustBe expected
    }
    "handle inclusions into contexts" in { (td: TestData) =>
      val (root, rpi) = checkFile("Context Includes", "includes/contextIncludes.riddl")
      val url = URL("file", "", "", defaultInputDir + "/includes/contextIncluded.riddl")
      val inc = StringParserInput("", url)
      root.domains mustNot be(empty)
      root.domains.head.contexts mustNot be(empty)
      root.domains.head.contexts.head.includes mustNot be(empty)
      root.domains.head.contexts.head.includes.head.contents.isEmpty mustNot be(true)
      val actual = root.domains.head.contexts.head.includes.head.contents.head
      val expected = Type(
        (1, 1, inc),
        Identifier((1, 6, inc), "foo"),
        String_((1, 12, inc)),
        Contents.empty()
      )
      actual mustBe expected
    }
    "handle 553-Contained-Group-References-Do-Not-Work" in { (td: TestData) =>
      val (root, _) = checkFile("Include Group", "includes/includer.riddl")
      root.domains mustNot be(empty)
      root.domains.head.includes.head.contents.isEmpty mustNot be(true)
    }
    "warn about duplicate includes" in { (td: TestData) =>
      val path = java.nio.file.Path.of(defaultInputDir + "/includes/duplicateInclude.riddl")
      val url = PathUtils.urlFromCwdPath(path)
      val future: Future[Assertion] = RiddlParserInput.fromURL(url, td).map { rpi =>
        TopLevelParser.parseInput(rpi) match {
          case Right(_) =>
            fail("Should have failed with warnings")
          case Left(messages) =>
            val errors = messages.justErrors
            if errors.nonEmpty then fail(errors.format)
            val warnings = messages.justWarnings
            warnings.size mustBe 1
            warnings.head.message must include("Duplicate include origin detected in")
            succeed
        }
      }
      Await.result(future, 10.seconds)
    }
  }

  "Import" should {
    "work syntactically" in { (td: TestData) =>
      val (root, _) = checkFile("Import", "import/import.riddl")
      root.domains must not(be(empty))
      root.domains.head.domains must not(be(empty))
      root.domains.head.domains.head.id.value must be("NotImplemented")
    }
  }
}
