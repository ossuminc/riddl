package com.ossuminc.riddl.language.parsing

class StatementsTest extends ParsingTest with Matchers{

  "Statements" must {
    "include Code Statement" in {
      val input =
        """domain CodeStatements is {
          |  context CodeStatements is {
          |    handler h is {
          |      on init {
          |        ```scala
          |          val foo: Int = 1
          |        ```
          |      }
          |    }
          |  }
          |}""".stripMargin
      TopLevelParser.parseString(input, origin = Some("Code Statement Test")) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val clause =
            AST.getContexts(AST.getTopLevelDomains(root).head).head.handlers.head.clauses.head
          val s: Statement = clause.statements.head
          s.isInstanceOf[CodeStatement] must be(true)
          val codeStatement = s.asInstanceOf[CodeStatement]
          codeStatement.language.s must be("scala")
          codeStatement.body must be(
            """val foo: Int = 1
              |        """.stripMargin)

    }
  }
}
