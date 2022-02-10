package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val errors = Validation.validate(
        RootContainer(
          Seq(Domain((0, 0), Identifier((0, 0), "foo")), Domain((1, 1), Identifier((1, 1), "foo")))
        ),
        ValidatingOptions.default
      )
      errors must not be empty
      errors.head.message must include("'foo' is defined multiple times")
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
