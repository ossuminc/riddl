/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.passes.Riddl

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val rpi = RiddlParserInput.empty
      val root = RootContainer(
        Seq(
          Domain((1, 1, rpi), Identifier((1, 7, rpi), "foo")),
          Domain((2, 2, rpi), Identifier((2, 8, rpi), "foo"))
        ),
        Seq(rpi)
      )

      runStandardPasses(root, CommonOptions(), true) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val theErrors: Messages = result.messages.justErrors
          theErrors mustBe empty
          val messages = result.messages.map(_.format)
          val notOccur = "Domain 'foo' overloads Domain 'foo' at empty(1:1)"
          messages.exists(_.contains(notOccur)) mustBe true
      }
    }

    "allow author information" in {
      val rpi = RiddlParserInput(
        """author Reid is {
                |    name: "Reid Spencer"
                |    email: "reid@ossuminc.com"
                |    organization: "Ossum Inc."
                |    title: "President"
                |  } described as "identifying"
                |domain foo by author Reid is {
                |  ???
                |} described as "example"
                |""".stripMargin
      )
      Riddl.parseAndValidate(rpi) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val domain = result.root.domains.head
          val author = result.root.authors.head
          domain must be(empty)
          domain.contents must be(empty)
          val expectedAuthor =
            Author(
            (1, 1, rpi),
            Identifier((1, 8, rpi), "Reid"),
            LiteralString((2, 11, rpi), "Reid Spencer"),
            LiteralString((3, 12, rpi), "reid@ossuminc.com"),
            Some(LiteralString((4, 19, rpi), "Ossum Inc.")),
            Some(LiteralString((5, 12, rpi), "President")),
            None,
            None,
            Some(
              BlockDescription(
                (6, 18, rpi),
                Seq(LiteralString((6, 18, rpi), "identifying"))
              )
            )
          )
          domain.authorDefs must be(empty)
          author must be(expectedAuthor)
          val expectedAuthorRef =
            AuthorRef((7, 12, rpi), PathIdentifier((7, 22, rpi), Seq("Reid")))
          domain.authors mustNot be(empty)
          domain.authors.head must be(expectedAuthorRef)
      }
    }
    "identify useless domain hierarchy" in {
      val input = """
                    |domain foo is {
                    |  domain bar is { ??? }
                    |}""".stripMargin
      parseAndValidateDomain(input) { (domain: Domain, _: RiddlParserInput, messages: Messages) =>
        domain mustNot be(empty)
        domain.contents mustNot be(empty)
        messages mustNot be(empty)
        messages.isOnlyIgnorable mustBe true
        messages.find(_.message contains "Singly nested") mustNot be(empty)
      }
    }
  }
}
