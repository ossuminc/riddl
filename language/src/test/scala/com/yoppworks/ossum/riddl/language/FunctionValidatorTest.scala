package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Entity
import com.yoppworks.ossum.riddl.language.AST.Identifier

class FunctionValidatorTest extends ValidatingTest {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in {
      parseAndValidate[Entity]("""
                                 |entity user is {
                                 |  function foo is {
                                 |    requires Boolean
                                 |    yields Integer
                                 |  }
                                 |}
                                 |""".stripMargin) { (e, msgs) =>
        e.functions must matchPattern {
          case Seq(
                AST.Function(
                  _,
                  Identifier(_, "foo"),
                  Some(AST.Bool(_)),
                  Some(AST.Integer(_)),
                  _,
                  None
                )
              ) =>
        }
        assert(msgs.exists(_.message == "Function 'foo' should have a description"))
      }
    }
  }
}
