/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ParsingTest
import com.reactific.riddl.language.ast.At

/** Unit Tests For CommonParser */
class CommonParserTest extends ParsingTest {

  "CommonParserTest" should {
    "location should construct from pair" in {
      val loc = At((1, 1))
      loc.line mustBe 1
      val column = loc.col
      column mustBe 1
    }
    "descriptions can be URLs" in {
      val input = """domain foo is { ??? } described at
                    |https://www.wordnik.com/words/phi""".stripMargin
      parseDomainDefinition(input, identity) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          val expected = Domain(
            (1, 1),
            Identifier((1, 8), "foo"),
            description = Some(
              URLDescription(
                (2, 1),
                new java.net.URL("https://www.wordnik.com/words/phi")
              )
            )
          )
          domain.toString mustBe expected.toString
      }
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
        case Right((actual, _)) =>
          val expected = LiteralString((1, 1), input.drop(1).dropRight(1))
          actual mustBe expected

      }
    }
    "literal strings can successfully escape a quote" in {
      val input =
        """domain foo is { ??? } explained as "this is an \"explanation\"" """
      parseDefinition[Domain](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((domain, _)) =>
          domain.description match {
            case Some(BlockDescription(_, lines)) =>
              lines.size mustBe 1
              lines.head.s mustBe "this is an \\\"explanation\\\""
            case x: Any => fail(s"Expected a one line Description but got: $x")
          }
      }
    }
    "OWASP email address works" in {
      val input =
        """type EmailAddress = Pattern("
          |^[a-zA-Z0-9_+&*-] + (?:\\.[a-zA-Z0-9_+&*-] + )*@(?:[a-zA-Z0-9-]+\\.) + [a-zA-Z]{2,7}
          |")
          |""".stripMargin
      parseDefinition[Type](input) match {
        case Left(errors) =>
          fail(errors.format)
        case Right((content, _)) =>
          content.typ match {
            case Pattern(_, Seq(LiteralString(_, str))) =>
              java.util.regex.Pattern.compile(str)
            case _ => fail("Expected a Pattern")
          }
      }
    }
    "literal strings can contain whitespace, hex and unicode escapes" in {
      val input = """"\\b\\n\\r\\t\\f\\x04\\u000a"""".stripMargin
      parse[LiteralString, LiteralString](
        input,
        StringParser("").literalString(_),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((actual, rpi)) =>
          actual mustBe
            LiteralString((1, 1, rpi), input.drop(1).dropRight(1))
      }
    }
  }
}
