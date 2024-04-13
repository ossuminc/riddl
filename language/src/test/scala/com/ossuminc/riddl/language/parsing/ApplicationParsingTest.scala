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
    "supports using the focus statement" in {
      val input =
        """
          |domain foo {
          |application foo2 {
          |  command GoHome {???} brief "Directive to focus on going to the home page"
          |  handler foo3 is {
          |    on command GoHome {
          |      focus on group g2
          |    }
          |  }
          |  group g2 is { ??? }
          |}
          |}""".stripMargin
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          succeed
      }
    }
    "supports 'shown by' in groups" in {
      val input =
        """
          |domain foo {
          |  application ignore {
          |    group Mickey shown by https://pngimg.com/uploads/mickey_mouse/mickey_mouse_PNG54.png is {
          |      ???
          |    }
          |  }
          |}
          |""".stripMargin
    }
  }
}
