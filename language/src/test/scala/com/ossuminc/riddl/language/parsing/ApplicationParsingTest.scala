package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import org.scalatest.matchers.must.Matchers

class ApplicationParsingTest extends ParsingTest with Matchers {

  "Application Components" must {
    "support nested empty definitions that fail" in {
      val input =
        """
          |domain foo {
          |application foo2 {
          |  group g1 is { ??? }
          |  group g2 is {
          |    group g3 is { ??? }
          |    input i1 acquires String is { ??? }
          |    output o1 displays String is { ??? }
          |  }
          |}
          |}""".stripMargin
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          succeed
      }
    }
  }
}
