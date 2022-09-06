package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Example
import com.reactific.riddl.language.testkit.ParsingTest

/** Unit Tests For Handler */
class GherkinTest extends ParsingTest {
  "Gherkin" should {
    "allow triviality" in {
      val input =
        """
          |example Triviality is { then "nothing of consequence" }
          |""".stripMargin
      parseDefinition[Example](input) match {
        case Left(errors) =>
          fail(errors.format)
        case Right(_) =>
          succeed
      }
    }
  }
}
