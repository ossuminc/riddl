package com.yoppworks.ossum.riddl.parser

import AST.LiteralString
import fastparse._
import ScalaWhitespace._

/** Unit Tests For CommonParserTest */
class CommonParserTest extends ParsingTest {

  "CommonParserTest" should {
    "literal strings can handle any chars except \"" in {
      val input =
        """"special chars: !@#$%^&*()_+-={}[];':",.<>/?~`
          | regular chars: abcdefghijklmnopqrstuvwxyz 0123456789
          | tab and newline chars:
          |"""".stripMargin
      checkParser[LiteralString, LiteralString](
        input,
        LiteralString(input.drop(1).dropRight(1)),
        CommonParser.literalString(_),
        _
      )
    }
  }
}
