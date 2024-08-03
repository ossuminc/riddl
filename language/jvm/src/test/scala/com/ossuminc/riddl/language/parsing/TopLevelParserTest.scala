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
import org.scalatest.TestData 

class TopLevelParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._


  val origin = "simpleDomain.riddl"

  val simpleDomainFile = new File(s"language/jvm/src/test/input/domains/$origin")
  val rpi: RiddlParserInput = rpiFromFile(simpleDomainFile)

  val simpleDomain: AST.Domain = Domain(
    At(1, 1, rpi),
    Identifier(At(1, 8, rpi), "foo")
  )
  val simpleDomainResults: AST.Root = Root(List(simpleDomain))

  "parse" should {
    "parse RiddlParserInput" in { (td:TestData) =>
      val input = rpiFromFile(simpleDomainFile)
      TopLevelParser.parseInput(input) mustBe Right(simpleDomainResults)
    }
    "parse File" in { (td:TestData) =>
      val input = rpiFromFile(simpleDomainFile)
      TopLevelParser.parseInput(input) mustBe Right(simpleDomainResults)
    }
    "parse String" in { (td:TestData) =>
      val source = Source.fromFile(simpleDomainFile)
      try {
        val stringContents = source.mkString
        val input = RiddlParserInput(stringContents, td)
        val result = TopLevelParser.parseInput(input)
        val expected = Root(List(simpleDomain))
        result mustBe Right(expected)
      } finally { source.close() }
    }
    "parse empty String" in { (td:TestData) =>
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

    "handle garbage" in { (td:TestData) =>
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
