package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{CodeStatement, Statement}
import org.scalatest.TestData

class StatementsTest extends ParsingTest{

  "Statements" must {
    "include Code Statement" in { (td:TestData) =>
      val input = RiddlParserInput(
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
          |}""".stripMargin,td)
      TopLevelParser.parseInput(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val clause = AST.getContexts(AST.getTopLevelDomains(root).head).head.handlers.head.clauses.head
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
