package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.StringParser

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
        case Right(content) => content mustBe LiteralString((1, 1), input.drop(1).dropRight(1))

      }
    }
    "literal strings can successfully escape a quote" in {
      val input = """domain foo is { ??? } explained as "this is an \"explanation\"" """
      parseDefinition[Domain](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(domain) => domain.description match {
            case Some(BlockDescription(_,lines)) =>
              lines.size mustBe 1
              lines.head.s mustBe "this is an \\\"explanation\\\""
            case x: Any => fail(s"Expected a one line Description but got: $x")
          }
      }
    }
    "literal strings can successfully handle a complicated email address pattern" in { // OC-137
      val input =
        """
          |type EmailAddress = Pattern("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-](?:\|.[a-z0-9!#$%&'*+/=?^_`{|}~-])|\"
          |(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])
          |\")@(?:(?:[a-z0-9](?:[a-z0-9-][a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-][a-z0-9])?|[(?:(?:25[0-5]|2[0-4]
          |[0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:
          |(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)])")
          |""".stripMargin.filterNot(_ == '\n')
      parseDefinition[Type](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content.typ match {
            case Pattern(_, Seq(LiteralString(_, str))) =>
              str.head mustBe '('
              str.last mustBe ')'
              str.contains("\\\"") mustBe true
            case _ => fail("Expected a Pattern")
          }
      }
    }
    "literal strings can contain whitespace and unicode escapes" in {
      val input = """"\\b\\n\\r\\t\\f\\u0000"""".stripMargin
      parse[LiteralString, LiteralString](
        input,
        StringParser("").literalString(_),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe LiteralString((1, 1), input.drop(1).dropRight(1))
      }
    }
  }
}
