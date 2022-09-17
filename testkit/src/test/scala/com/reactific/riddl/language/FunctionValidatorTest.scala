package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.testkit.ValidatingTest

class FunctionValidatorTest extends ValidatingTest {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in {
      parseAndValidateInContext[Entity]("""
                                          |entity user is {
                                          |  function foo is {
                                          |    requires {b: Boolean }
                                          |    returns {r: Integer }
                                          |  }
                                          |}
                                          |""".stripMargin) { (e, _, msgs) =>
        e.functions must matchPattern {
          case Seq(
                AST.Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Bool(_), _, _)))),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Integer(_), _, _)))),
                  _,
                  _,
                  _,
                  _,
                  _,
                  _,
                  _,
                  None,
                  None
                )
              ) =>
        }
        assert(
          msgs.exists(_.message == "Function 'foo' should have a description")
        )
      }
    }
    "validate function examples" in {
      val input = """
                    |  function AnAspect is {
                    |    EXAMPLE foobar {
                    |      GIVEN "everybody hates me"
                    |      AND "I'm depressed"
                    |      WHEN "I go fishing"
                    |      THEN "I'll just eat worms"
                    |      ELSE "I'm happy"
                    |    } described as {
                    |     "brief description"
                    |     "detailed description"
                    |    }
                    |  } described as "foo"
                    |""".stripMargin

      parseAndValidateInContext[Function](input) { case (feature, _, msgs) =>
        feature.id.value mustBe "AnAspect"
        assert(feature.examples.nonEmpty)
        assert(msgs.isEmpty)
        assert(msgs.forall(_.message.contains("should have a description")))
      }
    }
  }
}
