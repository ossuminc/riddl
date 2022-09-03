package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.testkit.ValidatingTest

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val rpi = RiddlParserInput.empty
      val theErrors: Messages = Validation.validate(
        RootContainer(
          Seq(
            Domain((1, 1, rpi), Identifier((1, 7, rpi), "foo")),
            Domain((2, 2, rpi), Identifier((2, 8, rpi), "foo"))
          ),
          Seq(rpi)
        ),
        CommonOptions()
      )
      theErrors must not be empty
      val messages = theErrors.map(_.format)
      val notOccur =
        "Style: empty(2:2): Domain 'foo' overloads Domain 'foo' at empty(1:1)"
      assert(messages.exists(_.startsWith(notOccur)))
    }

    "allow author information" in {
      val input = """domain foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@reactific.com"
                    |    organization: "Reactific Software Inc."
                    |    title: "President"
                    |  } described as "identifying"
                    |} described as "example"
                    |""".stripMargin
      parseAndValidate[Domain](input) {
        (domain: Domain, rpi: RiddlParserInput, messages: Messages) =>
          domain mustNot be(empty)
          domain.contents mustNot be(empty)
          val expectedAuthor = AuthorInfo(
            (2, 3, rpi), Identifier((2,10,rpi), "Reid"),
            LiteralString((3, 11, rpi), "Reid Spencer"),
            LiteralString((4, 12, rpi), "reid@reactific.com"),
            Some(LiteralString((5, 19, rpi), "Reactific Software Inc.")),
            Some(LiteralString((6, 12, rpi), "President")),
            None,None,Some(BlockDescription((7,18,rpi),
              Seq(LiteralString((7,18,rpi),"identifying"))))
          )
          domain.authors mustNot be(empty)
          domain.authors.head must be(expectedAuthor)
          messages mustBe empty
      }
    }
  }
}
