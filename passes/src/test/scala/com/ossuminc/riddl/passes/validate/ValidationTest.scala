/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}

import java.nio.file.Path

class ValidationTest extends ParsingTest {
  "ValidationMessage#format" should {
    "produce a correct string" in {
      val msg =
        Message(At(1, 2, RiddlParserInput.empty), "the_message", Warning)
      val content = msg.format
      val expected = """Warning: empty(1:2):
                       |the_message""".stripMargin
      content mustBe expected
    }
    "compare based on locations" in {
      val v1 = Message(At(1, 2, "the_source"), "the_message", Warning)
      val v2 = Message(At(2, 3, "the_source"), "the_message", Warning)
      v1 < v2 mustBe true
      v1 == v2 mustBe false
    }
  }

  "ValidationState" should {
    "parentOf" should {
      "find the parent of an existent child" in {
        val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
        val domain = Domain(At(), Identifier(At(), "foo"), Seq(aType))
        val root = Root(Seq(domain))
        val outputs = PassesOutput()
        val output = Pass.runSymbols(PassInput(root), outputs)
        output.parentOf(aType) mustBe Some(domain)
      }
      "not find the parent of a non-existent child" in {
        val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
        val domain = Domain(At(), Identifier(At(), "foo"))
        val root = Root(Seq(domain))
        val outputs = PassesOutput()
        val output = Pass.runSymbols(PassInput(root), outputs)
        output.parentOf(aType) mustBe None
      }
    }
  }

  "Validate All Things" must {
    var sharedRoot: Root = Root.empty

    "parse correctly" in {
      val rootFile = "language/src/test/input/full/domain.riddl"

      Riddl.parseAndValidate(Path.of(rootFile)) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          sharedRoot = result.root
          succeed
      }
    }
    "handle includes" in {
      sharedRoot.domains.headOption match {
        case Some(domain) =>
          val incls = domain.includes
          incls mustNot be(empty)
          incls.head.contents mustNot be(empty)
          incls.head.contents.head.getClass mustBe classOf[Application]
          incls(1).contents.head.getClass mustBe classOf[Context]
        case None => fail("There should be a domain")
      }
    }
    "have terms and author refs in applications" in {
      sharedRoot.domains.headOption match {
        case Some(domain) =>
          val include = domain.includes.head
          val apps = include.contents.filter[Application]
          apps mustNot be(empty)
          apps.head mustBe a[Application]
          val app: Application = apps.head
          app.terms mustNot be(empty)
          app.hasAuthors mustBe false
          app.hasAuthorRefs mustBe true
          app.authorRefs mustNot be(empty)
        case None => fail("There should be a domain")
      }
    }
  }
}
