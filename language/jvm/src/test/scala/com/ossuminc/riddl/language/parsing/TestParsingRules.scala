package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.pc
import com.ossuminc.riddl.language.AST.LiteralString
import com.ossuminc.riddl.language.At
import fastparse.Parsed.{Extra, Failure, Success}
import org.scalatest.TestData

class TestParsingRules extends FastParseTest with NoWhiteSpaceParsers {

  "NoWhiteSpaceParser" must {
    "recognize toEndOfLine" in { (td: TestData) =>
      val input = RiddlParserInput("This is some text to parse", td)
      val result = testRule[String](input, toEndOfLine)
      result must be(input.data)
    }
    "recognize until" in { (td: TestData) =>
      val input = RiddlParserInput("foobarAB ", td)
      val result = testRule[String](input, until('A', 'B'))
      result must be("foobarAB")
    }
    "recognize until3" in { (td: TestData) =>
      val input = RiddlParserInput("foobarABC ", td)
      val result = testRule[String](input, until3('A', 'B', 'C'))
      result must be("foobar")
    }
    "recognize markDownLink" in { (td: TestData) =>
      val input = RiddlParserInput("| LiteralString", td)
      val result = testRule[LiteralString](input, markdownLine)
      result.loc must be(At((1, 1)))
      result.s must be(" LiteralString")
    }

    "recognize literalString" in { (td: TestData) =>
      val input = RiddlParserInput("\"String\\f\\n\\a\\e\\r\\t\\x0706\\u43FF\"", td)
      val result = testRule[LiteralString](input, literalString)
      result.loc must be(At((1, 1)))
      result.s must be("String\\f\\n\\a\\e\\r\\t\\x0706\\u43FF")
    }
  }
}
