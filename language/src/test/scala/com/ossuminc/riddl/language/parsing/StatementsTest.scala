package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers

class StatementsTest extends ParsingTest with Matchers{

  "Statements" must {
    "include Code Statement" in {
      val input =
        """domain CodeStatements is {
          |  context CodeStatements is {
          |    handler h is {
          |      on initialization {
          |        ```scala
          |          val foo: Int = 1
          |        ```
          |      }
          |    }
          |  }
          |}""".stripMargin
      TopLevelParser.parseString(input, origin = Some("Code Statement Test")) match
        case Left(value) => ???
        case Right(root) => ???

    }
  } 
}
