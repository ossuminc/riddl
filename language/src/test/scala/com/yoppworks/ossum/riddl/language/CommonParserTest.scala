package com.yoppworks.ossum.riddl.language

import AST.LiteralString
import AST.Location

/** Unit Tests For CommonParser */
class CommonParserTest extends ParsingTest {

  "CommonParserTest" should {
    "location should construct from pair" in {
      val loc = Location((1, 1))
      loc.line mustBe 1
      loc.col mustBe 1
    }
    "literal strings can handle any chars except \"" in {
      val input = """"special chars: !@#$%^&*()_+-={}[];':,.<>/?~`
                    | regular chars: abcdefghijklmnopqrstuvwxyz 0123456789
                    | tab and newline chars:
                    |"""".stripMargin
      parse[LiteralString, LiteralString](
        input,
        StringParser("").literalString(_),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe
            LiteralString((1, 1), input.drop(1).dropRight(1))

      }
    }
  }
}
