package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*

class FunctionValidatorTest extends ValidatingTest {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in {
      parseAndValidateInContext[Entity]("""
                                          |entity user is {
                                          |  function foo is {
                                          |    requires {b: Boolean }
                                          |    yields {r: Integer }
                                          |  }
                                          |}
                                          |""".stripMargin) { (e, msgs) =>
        e.functions must matchPattern {
          case Seq(
                AST.Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Bool(_), _, _)))),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Integer(_), _, _)))),
                  _,
                  None,
                  None
                )
              ) =>
        }
        assert(msgs.exists(_.message == "Function 'foo' should have a description"))
      }
    }
  }
}
