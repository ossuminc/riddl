/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{AbstractParsingTest, RiddlParserInput, StringParserInput}
import com.ossuminc.riddl.passes.{Pass, PassesOutput, PassInput, Riddl}
import com.ossuminc.riddl.passes.PassRoot
import com.ossuminc.riddl.utils.{pc, CommonOptions, PlatformContext}

import scala.io.AnsiColor.{BOLD, RESET}
import org.scalatest.TestData

abstract class SharedValidationTest(using PlatformContext) extends AbstractParsingTest {

  "ValidationMessage#format" should {
    "produce a correct string" in { (td: TestData) =>
      val rpi = RiddlParserInput("abcdefg", td)
      val at = At(rpi, 0, 6)
      val msg = Message(at, "the_message", Warning)
      pc.withOptions[org.scalatest.Assertion](CommonOptions.default.copy(noANSIMessages = true)) { _ =>
        val content = msg.format
        val expected =
          s"""empty(1:1->7):
            |the_message:
            |abcdefg""".stripMargin
        content must be(expected)
      }
    }
    "compare based on locations" in { (td: TestData) =>
      val rpi = RiddlParserInput("the_source", td)
      val v1 = Message(At(1, 2, rpi), "the_message", Warning)
      val v2 = Message(At(2, 3, rpi), "the_message", Warning)
      (v1 < v2).mustBe(true)
      (v1 == v2).mustBe(false)
    }
  }

  "SymbolsOutput.parentOf" should {
    "find the parent of an existent child" in { (_: TestData) =>
      val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
      val domain = Domain(At(), Identifier(At(), "foo"), Contents(aType))
      val root = Root(At(), Contents(domain))
      val outputs = PassesOutput()
      val output = Pass.runSymbols(PassInput(root), outputs)
      output.parentOf(aType) must be(Some(domain))
    }
    "not find the parent of a non-existent child" in { (_: TestData) =>
      val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
      val domain = Domain(At(), Identifier(At(), "foo"))
      val root = Root(At(), Contents(domain))
      val outputs = PassesOutput()
      val output = Pass.runSymbols(PassInput(root), outputs)
      output.parentOf(aType) must be(None)
    }
  }

  "Validate All Things" must {
    var sharedRoot: PassRoot = Root.empty

    "parse and Validate correctly" in { (td: TestData) =>
      val input =
        """author Reid is { name: "Reid Spencer" email: "reid@ossum.biz" }
          |
          |domain full is {
          |  user Doer is "that which does"
          |  type Something is Abstract
          |  context dosomething is { ??? } with {
          |    by author Reid
          |    term term is "Terminal"
          |  }
          |
          |  context other  is {
          |    ???
          |  } with {
          |    by author Reid
          |  }
          |  context context is {
          |    adaptor adaptor from context other is { ??? }
          |    function function is { ??? }
          |    event event is { at: TimeStamp }
          |    handler handler is {
          |      on event event {
          |        when "there is an error" then
          |          error "This is an error"
          |        end
          |      }
          |    }
          |    repository repository is { ??? }
          |    source source is { ??? }
          |    sink sink is { ??? }
          |    flow flow is { ??? }
          |    merge merge is { ??? }
          |    split split is { ??? }
          |    saga saga is {
          |     step a is {
          |       prompt "a.1"
          |     } reverted by {
          |       prompt "a_r.1"
          |     }
          |     step b is {
          |       prompt "b.1"
          |     } reverted by {
          |       prompt "b_r.1"
          |     }
          |   }
          |  } with {
          |    by author Reid
          |    term term is "not interesting"
          |  }
          |} with {
          |  by author Reid
          |  term Idea is "an abstract notion"
          |}
          |
          |""".stripMargin
      val rpi = RiddlParserInput(input, td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) if errors.hasErrors =>
          println(errors.format)
          fail(errors.justErrors.format)
        case Left(errors) =>
          fail(s"Parse failed but no errors: ${errors.format}")
        case Right(result) =>
          sharedRoot = result.root
          succeed
      }
    }
    "handle includes" in { (_: TestData) =>
      sharedRoot.contents.filter[Domain].headOption match {
        case Some(domain) =>
          domain.contents.isEmpty.mustNot(be(true))
          domain.contents.find("dosomething").getOrElse(None).getClass.mustBe(classOf[Context])
          domain.contents(3).getClass mustBe classOf[Context]
        case None => fail("There should be a domain")
      }
    }
    "have terms and author refs in contexts" in { (_: TestData) =>
      sharedRoot.contents.filter[Domain].headOption match {
        case Some(domain) =>
          val apps = domain.contents.filter[Context]
          apps.mustNot(be(empty))
          apps.head mustBe a[Context]
          val app: Context = apps.head
          app.terms mustNot be(empty)
          app.hasAuthors mustBe false
          app.hasAuthorRefs mustBe true
          app.authorRefs mustNot be(empty)
        case None => fail("There should be a domain")
      }
    }
  }
}
