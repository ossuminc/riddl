/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

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
        case Right(_) =>
          fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle bad URL" in {
      val badURL = new java.net.URL("https://incredible.lightness.of.being:8900000/@@@")
      parseDomainDefinition(
        RiddlParserInput(badURL),
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
      val emptyURL = new java.net.URL("https://raw.githubusercontent.com/reactific/riddl/main/testkit/src/test/input/domains/simpleDomain2.riddl")
      parseDomainDefinition(
        RiddlParserInput(emptyURL),
        identity
      ) match {
        case Right(_) =>
          fail("Should have gotten 'port out of range' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("port out of range: 8900000"))
      }
    }
    "handle existing URL" in {
      val fullURL = new java.net.URL("https://raw.githubusercontent.com/reactific/riddl/main/testkit/src/test/input/domains/simpleDomain.riddl")
      parseDomainDefinition(
        RiddlParserInput(fullURL),
        identity
      ) match {
        case Right(_) =>
          succeed
        case Left(errors) =>
          fail(errors.format)
      }
    }
    "handle inclusions into domain" in {
      val rc = checkFile("Domain Includes", "domainIncludes.riddl")
      rc.contents mustNot be(empty)
      rc.domains.head.includes mustNot be(empty)
      rc.domains.head.includes.head.contents mustNot be(empty)
      rc.domains.head.includes.head.contents.head mustBe Type(
        (1, 1, rc.inputs.head),
        Identifier((1, 6, rc.inputs.head), "foo"),
        Strng((1, 13, rc.inputs.head)),
        None
      )
    }
    "handle inclusions into contexts" in {
      val rc = checkFile("Context Includes", "contextIncludes.riddl")
      rc.domains mustNot be(empty)
      rc.domains.head.contexts mustNot be(empty)
      rc.domains.head.contexts.head.includes mustNot be(empty)
      rc.domains.head.contexts.head.includes.head.contents mustNot be(empty)
      rc.domains.head.contexts.head.includes.head.contents.head mustBe Type(
        (1, 1, rc.inputs.head),
        Identifier((1, 6, rc.inputs.head), "foo"),
        Strng((1, 12, rc.inputs.head)),
        None
      )
    }
  }

  "Import" should {
    "work syntactically" in {
      val root = checkFile("Import", "import.riddl")
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
