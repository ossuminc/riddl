package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Example
import com.reactific.riddl.language.testkit.ParsingTest

/** Unit Tests For Handler */
class GherkinTest extends ParsingTest {
  "Examples" should {
    "allow triviality" in {
      val input = """
                    |example Triviality is { then "nothing of consequence" }
                    |""".stripMargin
      parseDefinition[Example](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
  }
}
