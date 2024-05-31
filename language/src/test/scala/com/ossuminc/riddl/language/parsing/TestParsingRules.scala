package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.LiteralString
import com.ossuminc.riddl.language.At
import fastparse.Parsed.{Extra, Failure, Success}

class TestParsingRules extends FastParseTest with NoWhiteSpaceParsers {

  "NoWhiteSpaceParser" must {
    "recognize toEndOfLine" in {
      val input = RiddlParserInput("This is some text to parse", "TestParsingRules")
      val result = testRule[String](input, toEndOfLine)
      result must be(input.data)
      }
    }
    "recognize until" in {
      val input = RiddlParserInput("foobarAB ")
      val result = testRule[String](input, until('A', 'B'))
      result must be("foobarAB")
    }
    "recognize until3" in {
      val input = RiddlParserInput("foobarABC ")
      val result = testRule[String](input, until3('A', 'B', 'C'))
      result must be("foobar")
    }
    "recognize markDownLink" in {
     val input = RiddlParserInput("| LiteralString")
      val result = testRule[LiteralString](input, markdownLine)
      result.loc must be(At((1,1)))
      result.s must be(" LiteralString")
    }

    "recognize "
    "recognize literalString" in {
      val input = RiddlParserInput("\"String\\f\\n\\a\\e\\r\\t\\x0706\\u43FF\"")
      val result = testRule[LiteralString](input, literalString)
      result.loc must be(At((1,1)))
      result.s must be("String\\f\\n\\a\\e\\r\\t\\x0706\\u43FF")
    }


}
