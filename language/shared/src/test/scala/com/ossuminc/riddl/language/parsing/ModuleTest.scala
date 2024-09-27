package com.ossuminc.riddl.language.parsing

import org.scalatest.TestData

class ModuleTest extends NoJVMParsingTest {

  "Module" should {
    "be accepted at root scope" in { (td: TestData) => 
      val input = RiddlParserInput(
        """
          |module foo is {
          |   // this is a comment
          |   domain blah is { ??? }
          |}
          |""".stripMargin, td
      )
      parseTopLevelDomains(input) match
        case Left(messages) => fail(messages.format)
        case Right(root) => 
          root.modules must not be(empty)
          root.modules.head.id.value must be("foo")
          root.modules.head.domains must not be(empty)
          root.modules.head.domains.head.id.value must be("blah")
          
    }
  }

}
