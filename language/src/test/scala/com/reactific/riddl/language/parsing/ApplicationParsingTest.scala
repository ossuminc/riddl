package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ParsingTest
import org.scalatest.matchers.must.Matchers

class ApplicationParsingTest extends ParsingTest with Matchers{

  "Application Components" must {
    "empty definitions should fail" in {
      val input =
        """
          |domain foo {
          |application fooer {
          |  group {}
          |}
          |}""".stripMargin
      parseDefinition[Domain](input) match {
        case Left(msgs: Messages) =>
          val errors = msgs.justErrors
          errors.size mustBe(1)
          val msg = errors.head
          msg.message contains("Expected one of")
          msg.message contains("???")
          msg.loc.line mustBe(4)
        case Right((dom: Domain, _)) =>
          fail("should have required ??? syntax")
      }
    }
  }
}
