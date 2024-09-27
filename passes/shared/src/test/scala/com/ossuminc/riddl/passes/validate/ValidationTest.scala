/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{NoJVMParsingTest, RiddlParserInput, StringParserInput}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import org.scalatest.TestData

class ValidationTest extends NoJVMParsingTest {
  "ValidationMessage#format" should {
    "produce a correct string" in { (td:TestData) =>
      val at = At(1, 2, RiddlParserInput("abcdefg",td))
      val msg =
        Message(at, "the_message", Warning)
      val content = msg.format
      val expected =
        """empty(1:2):
          |the_message:
          |abcdefg
          | ^""".stripMargin
      content mustBe expected
    }
    "compare based on locations" in { (td:TestData) =>
      val rpi = RiddlParserInput("the_source", td)
      val v1 = Message(At(1, 2, rpi), "the_message", Warning)
      val v2 = Message(At(2, 3, rpi), "the_message", Warning)
      (v1 < v2).mustBe(true)
      (v1 == v2).mustBe(false)
    }
  }

  "SymbolsOutput.parentOf" should {
    "find the parent of an existent child" in { (td:TestData) =>
      val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
      val domain = Domain(At(), Identifier(At(), "foo"), Contents(aType))
      val root = Root(Contents(domain))
      val outputs = PassesOutput()
      val output = Pass.runSymbols(PassInput(root), outputs)
      output.parentOf(aType) mustBe Some(domain)
    }
    "not find the parent of a non-existent child" in { (td:TestData) =>
      val aType = Type(At(), Identifier(At(), "bar"), String_(At()))
      val domain = Domain(At(), Identifier(At(), "foo"))
      val root = Root(Contents(domain))
      val outputs = PassesOutput()
      val output = Pass.runSymbols(PassInput(root), outputs)
      output.parentOf(aType) mustBe None
    }
  }

  "Validate All Things" must {
    var sharedRoot: Root = Root.empty

    "parse and Validate correctly" in { (td:TestData) =>
      val input =
        """author Reid is { name: "Reid Spencer" email: "reid@ossum.biz" }
          |
          |domain full is {
          |  user Doer is "that which does"
          |  type Something is Abstract
          |  application dosomething is { ??? } with {
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
          |        if "there is an error" then {
          |          error "This is an error"
          |        }
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
          |       "a.1"
          |     } reverted by {
          |       "a_r.1"
          |     }
          |     step b is {
          |       "b.1"
          |     } reverted by {
          |       "b_r.1"
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
        case Right(result) =>
          sharedRoot = result.root
          succeed
      }
    }
    "handle includes" in { (_:TestData) =>
      
      sharedRoot.domains.headOption match {
        case Some(domain) =>
          domain.contents mustNot be(empty)
          domain.contents.find("dosomething").getOrElse(None).getClass mustBe classOf[Application]
          domain.contents(3).getClass mustBe classOf[Context]
        case None => fail("There should be a domain")
      }
    }
    "have terms and author refs in applications" in { (_:TestData) =>
      sharedRoot.domains.headOption match {
        case Some(domain) =>
          val apps = domain.contents.filter[Application]
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
