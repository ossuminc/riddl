/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.passes.Pass

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
        Seq.empty[Author],
        Seq(rpi)
      )

      Pass(root, CommonOptions(), true) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val theErrors: Messages = result.messages.justErrors
          theErrors mustBe empty
          val messages = result.messages.map(_.format)
          val notOccur =
            "Style: empty(2:2): Domain 'foo' overloads Domain 'foo' at empty(1:1)"
          messages.exists(_.startsWith(notOccur)) mustBe true
      }
    }

    "allow author information" in {
      val input = """domain foo by author Reid is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@reactific.com"
                    |    organization: "Reactific Software Inc."
                    |    title: "President"
                    |  } described as "identifying"
                    |} described as "example"
                    |""".stripMargin
      parseAndValidateDomain(input) {
        (domain: Domain, rpi: RiddlParserInput, messages: Messages) =>
          domain mustNot be(empty)
          domain.contents mustNot be(empty)
          val expectedAuthor = Author(
            (2, 3, rpi),
            Identifier((2, 10, rpi), "Reid"),
            LiteralString((3, 11, rpi), "Reid Spencer"),
            LiteralString((4, 12, rpi), "reid@reactific.com"),
            Some(LiteralString((5, 19, rpi), "Reactific Software Inc.")),
            Some(LiteralString((6, 12, rpi), "President")),
            None,
            None,
            Some(BlockDescription(
              (7, 18, rpi),
              Seq(LiteralString((7, 18, rpi), "identifying"))
            ))
          )
          domain.authorDefs mustNot be(empty)
          domain.authorDefs.head must be(expectedAuthor)
          val expectedAuthorRef =
            AuthorRef((1, 12, rpi), PathIdentifier((1, 22, rpi), Seq("Reid")))
          domain.authors mustNot be(empty)
          domain.authors.head must be(expectedAuthorRef)
          messages.isOnlyIgnorable mustBe true
      }
    }
    "identify useless domain hierarchy" in {
      val input = """
                    |domain foo is {
                    |  domain bar is { ??? }
                    |}""".stripMargin
      parseAndValidateDomain(input) {
        (domain: Domain, _: RiddlParserInput, messages: Messages) =>
          domain mustNot be(empty)
          domain.contents mustNot be(empty)
          messages mustNot be(empty)
          messages.isOnlyIgnorable mustBe true
          messages.find(_.message contains "Singly nested") mustNot be(empty)
      }
    }
  }
}
