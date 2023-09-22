/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.{AST, ParsingTestBase}

import java.io.File
import scala.io.Source

class TopLevelParserTest extends ParsingTestBase {

  val origin = "simpleDomain.riddl"

  val simpleDomainFile = new File(s"language/src/test/input/domains/$origin")
  val rip: RiddlParserInput = RiddlParserInput(simpleDomainFile)

  val simpleDomain: AST.Domain = Domain(
    At(1, 1, rip),
    Identifier(At(1, 8, rip), "foo"),
    Seq.empty[DomainOption],
    Seq.empty[AuthorRef],
    Seq.empty[Author]
  )
  val simpleDomainResults: AST.RootContainer = RootContainer(
    List(simpleDomain),
    List(
      RiddlParserInput(
        new File("language/src/test/input/domains/simpleDomain.riddl")
      )
    )
  )

  "parse" should {
    "parse RiddlParserInput" in {
      TopLevelParser.parse(RiddlParserInput(simpleDomainFile)) mustBe
        Right(simpleDomainResults)
    }
    "parse File" in {
      TopLevelParser.parse(simpleDomainFile) mustBe Right(simpleDomainResults)
    }
    "parse Path" in {
      TopLevelParser.parse(simpleDomainFile.toPath) mustBe
        Right(simpleDomainResults)
    }
    "parse String" in {
      val source = Source.fromFile(simpleDomainFile)
      try {
        val stringContents = source.mkString
        val result = TopLevelParser.parse(stringContents, origin)
        val expected = RootContainer(
          List(simpleDomain),
          List(
            StringParserInput(
              """domain foo is {
              |  ???
              |}
              |""".stripMargin,
              "simpleDomain.riddl"
            )
          )
        )
        result mustBe Right(expected)
      } finally { source.close() }
    }
    "parse empty String" in {
      val expected =
        RootContainer(List(), List(StringParserInput("", "string")))
      TopLevelParser.parse("") match {
        case Right(expected) =>
          fail("Should have failed excpecting an author or domain")
        case Left(messages) =>
          messages.length mustBe(1)
          val msg = messages.head
          msg.message must include("Expected one of (\"author\" | \"domain\"")
      }
    }

    "handle garbage" in {
      val expected =
        RootContainer(List(), List(StringParserInput("", "string")))
      TopLevelParser.parse(" pweio afhj") match {
        case Right(expected) =>
          fail("Should have failed excpecting an author or domain")
        case Left(messages) =>
          messages.length mustBe(1)
          val msg = messages.head
          msg.message must include("Expected one of (\"author\" | \"domain\"")
      }
    }
  }
}
