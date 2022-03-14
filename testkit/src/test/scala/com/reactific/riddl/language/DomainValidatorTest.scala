package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.testkit.ValidatingTest

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val theErrors: Validation.ValidationMessages =
        Validation.validate(
        RootContainer(
          Seq(Domain((1, 1), Identifier((1, 7), "foo")), Domain((2, 2), Identifier((2, 8), "foo")))
        ),
        CommonOptions()
      )
      theErrors must not be empty
      val messages = theErrors.map(_.format)
      val notOccur = "Style: default(2:2): Domain 'foo' overloads Domain 'foo' at default(1:1)"
      assert(messages.exists(_.startsWith(notOccur)))
    }
    "allow author information" in {
      val input = """domain foo is {
                    |  author is {
                    |    name: "Reid Spencer"
                    |    email: "reid.spencer@yoppworks.com"
                    |    organization: "Yoppworks, Inc."
                    |    title: "VP Technology"
                    |  }
                    |} described as "example"
                    |""".stripMargin
      parseAndValidate[Domain](input) { (domain, messages) =>
        domain mustNot be(empty)
        domain.contents mustBe empty
        domain.author mustBe Some(AuthorInfo(
          2 -> 3,
          LiteralString(3 -> 11, "Reid Spencer"),
          LiteralString(4 -> 12, "reid.spencer@yoppworks.com"),
          Some(LiteralString(5 -> 19, "Yoppworks, Inc.")),
          Some(LiteralString(6 -> 12, "VP Technology"))
        ))
        messages mustBe empty
      }
    }
  }
}
