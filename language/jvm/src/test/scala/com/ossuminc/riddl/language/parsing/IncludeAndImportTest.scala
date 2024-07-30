/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*

import java.nio.file.Path
import com.ossuminc.riddl.utils.URL

/** Unit Tests For Includes */
class IncludeAndImportTest extends ParsingTest {

  "Include" should {
    "handle missing files" in {
      parseDomainDefinition(
        RiddlParserInput(
          "domain foo is { include \"unexisting\" } explained as \"foo\""
        ),
        identity
      ) match {
        case Right(result) =>
          fail(s"Should have gotten 'FileNotFoundException' but succeeded with: ${result._1}")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle bad URL" in {
      parseDomainDefinition(
        "include \"https://incredible.lightness.of.being:8900000/@@@\"",
        identity
      ) match {
        case Right(_) =>
          fail("Should have gotten 'port out of range' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("port out of range: 8900000"))
      }
    }
    "handle non existent URL" in {
      val nonExistentURL = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/testkit/src/test/input/domains/simpleDomain2.riddl"
      )
      intercept[java.io.FileNotFoundException] {
        parseDomainDefinition(
          RiddlParserInput(nonExistentURL),
          identity
        ) match {
          case Right(_) =>
            fail("Should have gotten 'port out of range' error")
          case Left(errors) =>
            errors.size must be(1)
            errors.exists(_.format.contains("port out of range: 8900000"))
        }
      }
    }
    "handle existing URI" in {
      import com.ossuminc.riddl.utils.URL
      val cwd = System.getProperty("user.dir", ".")
      val urlStr: String = s"file:///$cwd/testkit/src/test/input/domains/simpleDomain.riddl"
      val uri = URL(urlStr)
      parseDomainDefinition(
        RiddlParserInput(uri),
        identity
      ) match {
        case Right(_) =>
          succeed
        case Left(errors) =>
          fail(errors.format)
      }
    }
    "handle inclusions into domain" in {
      val rc = checkFile("Domain Includes", "includes/domainIncludes.riddl")
      val inc = StringParserInput("", "domainIncluded.riddl")
      rc.domains mustNot be(empty)
      rc.domains.head.includes mustNot be(empty)
      rc.domains.head.includes.head.contents mustNot be(empty)
      val actual = rc.domains.head.includes.head.contents.head
      val expected = Type(
        (1, 1, inc),
        Identifier((1, 6, inc), "foo"),
        String_((1, 13, inc)),
        None
      )
      actual mustBe expected
    }
    "handle inclusions into contexts" in {
      val rc = checkFile("Context Includes", "includes/contextIncludes.riddl")
      val inc = StringParserInput("", "contextIncluded.riddl")
      rc.domains mustNot be(empty)
      rc.domains.head.contexts mustNot be(empty)
      rc.domains.head.contexts.head.includes mustNot be(empty)
      rc.domains.head.contexts.head.includes.head.contents mustNot be(empty)
      val actual = rc.domains.head.contexts.head.includes.head.contents.head
      val expected = Type(
        (1, 1, inc),
        Identifier((1, 6, inc), "foo"),
        String_((1, 12, inc)),
        None
      )
      actual mustBe expected
    }
    "handle 553-Contained-Group-References-Do-Not-Work" in {
      val root = checkFile("Include Group", "includes/includer.riddl")
      root.domains mustNot be(empty)
      root.domains.head.includes.head.contents mustNot be(empty)
    }
    "warn about duplicate includes" in {
      val path = Path.of("language/jvm/src/test/input/includes/duplicateInclude.riddl")
      val input = RiddlParserInput(path)
      TopLevelParser.parseInput(input) match {
        case Right(_) =>
          fail("Should have failed with warnings")
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.nonEmpty then fail(errors.format)
          val warnings = messages.justWarnings
          warnings.size mustBe 1
          warnings.head.message must include("Duplicate include origin detected in someTypes")
          succeed
      }

    }
  }

  "Import" should {
    "work syntactically" in {
      val root = checkFile("Import", "import/import.riddl")
      root.domains must not(be(empty))
      root.domains.head.domains must not(be(empty))
      root.domains.head.domains.head.id.value must be("NotImplemented")
    }
    "handle missing files" in {
      val input =
        "domain foo is { import domain foo from \"nonexisting\" } described as \"foo\""
      parseDomainDefinition(RiddlParserInput(input), identity) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
  }
}
