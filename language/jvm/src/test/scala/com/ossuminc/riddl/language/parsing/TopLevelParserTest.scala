/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{AST, At}

import java.io.File
import scala.io.Source

class TopLevelParserTest extends ParsingTest {

  val origin = "simpleDomain.riddl"

  val simpleDomainFile = new File(s"language/jvm/src/test/input/domains/$origin")
  val rip: RiddlParserInput = RiddlParserInput(simpleDomainFile)

  val simpleDomain: AST.Domain = Domain(
    At(1, 1, rip),
    Identifier(At(1, 8, rip), "foo")
  )
  val simpleDomainResults: AST.Root = Root(List(simpleDomain))

  "parse" should {
    "parse RiddlParserInput" in {
      TopLevelParser.parseInput(RiddlParserInput(simpleDomainFile)) mustBe
        Right(simpleDomainResults)
    }
    "parse File" in {
      TopLevelParser.parseFile(simpleDomainFile) mustBe Right(simpleDomainResults)
    }
    "parse String" in {
      val source = Source.fromFile(simpleDomainFile)
      try {
        val stringContents = source.mkString
        val result = TopLevelParser.parseString(stringContents, origin = Some(simpleDomainFile.getName))
        val expected = Root(List(simpleDomain))
        result mustBe Right(expected)
      } finally { source.close() }
    }
    "parse empty String" in {
      val expected = Root(List())
      val parser = StringParser("")
      parser.parseRoot() match {
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

    "handle garbage" in {
      TopLevelParser.parseString(" pweio afhj") match {
        case Right(_) =>
          fail("Should have failed; expecting an author or domain")
        case Left(messages) =>
          messages.length mustBe 1
          val msg = messages.head
          msg.message must include("Expected one of")
          msg.message must include("\"author\"")
          msg.message must include("\"domain\"")
      }
    }
  }
}
