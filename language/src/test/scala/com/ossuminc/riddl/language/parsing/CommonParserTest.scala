/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{PlatformContext, URL}
import org.scalatest.matchers.must.Matchers.must
import org.scalatest.{TestData, fixture}
import sourcecode.Text.generate

import scala.concurrent.ExecutionContext.Implicits.global

/** Unit Tests For CommonParser */
abstract class CommonParserTest(using PlatformContext) extends AbstractParsingTest {

  "NonWhiteSpaceParsers" should {
    "handle a literalString" in { (td: TestData) =>
      val content = "This is a literal string with"
      val text = s""""$content""""
      val input = RiddlParserInput(text, td)
      val testParser = TestParser(input)
      testParser.expect[LiteralString](p => testParser.literalString(using p)) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(ls)      => ls.s must be(content)
    }
  }

  "CommonParser" should {
    "allow location to construct from pair" in { (td: TestData) =>
      val loc = At((1, 1))
      loc.line mustBe 1
      val column = loc.col
      column mustBe 1
    }

    "allow descriptions to be URLs" in { (td: TestData) =>
      import com.ossuminc.riddl.utils.URL
      val input = RiddlParserInput(
        """domain foo is { ??? } with { described at https://www.wordnik.com/words/phi }""".stripMargin,
        URL.empty,
        td.name
      )
      parseDomainDefinition(input, identity) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          val expected = Domain(
            (1, 1),
            Identifier((1, 8), "foo"),
            contents = Contents.empty(),
            metadata = Contents(
              URLDescription(
                (1, 30),
                URL("https://www.wordnik.com/words/phi")
              )
            )
          )
          val result = domain must be(expected)
          result
      }
    }

    "literal strings can handle any chars except \"" in { (td: TestData) =>
      val input: RiddlParserInput = RiddlParserInput(
        """"special chars: !@#$%^&*()_+-={}[];':,.<>/?~`
           | regular chars: abcdefghijklmnopqrstuvwxyz 0123456789
           | tab and newline chars:
           |"""".stripMargin,
        td
      )
      parse[LiteralString, LiteralString](
        input,
        p => StringParser("").literalString(using p),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((actual, _)) =>
          val expected = LiteralString((1, 1), input.data.drop(1).dropRight(1))
          actual mustBe expected

      }
    }
    "literal strings can successfully escape a quote" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { ??? } with { briefly as "this is an \"explanation\"" } """,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((domain: Domain, _)) =>
          domain.briefString must be("this is an \\\"explanation\\\"")
      }
    }
    "OWASP email address works" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type EmailAddress = Pattern("
          |^[a-zA-Z0-9_+&*-] + (?:\\.[a-zA-Z0-9_+&*-] + )*@(?:[a-zA-Z0-9-]+\\.) + [a-zA-Z]{2,7}
          |")
          |""".stripMargin,
        td
      )
      parseDefinition[Type](input) match {
        case Left(errors) =>
          fail(errors.format)
        case Right((content: Type, _)) =>
          content.typEx match {
            case Pattern(_, Seq(LiteralString(_, str))) =>
              java.util.regex.Pattern.compile(str)
            case _ => fail("Expected a Pattern")
          }
      }
    }
    "literal strings can contain whitespace, hex and unicode escapes" in { (td: TestData) =>
      val input = RiddlParserInput(""""\\b\\n\\r\\t\\f\\x04\\u000a"""".stripMargin, td)
      parse[LiteralString, LiteralString](
        input,
        p => StringParser("").literalString(using p),
        identity
      ) match {
        case Left(errors) =>
          fail(errors.format)
        case Right((actual, rpi)) =>
          actual mustBe LiteralString((1, 1, rpi), input.data.drop(1).dropRight(1))
      }
    }
  }
  "ParseErrorLocation" should {
    "report error at end of offending line, not start of next line" in { (td: TestData) =>
      // "type Money is Currency" is missing the type arguments e.g. Currency(USD)
      // The error should point to line 2 (where "Currency" ends), not line 3
      val input = RiddlParserInput(
        "domain foo is {\n  type Money is Currency\n}\n",
        td
      )
      parseTopLevelDomains(input) match
        case Right(_) => fail("Expected parse failure but parsing succeeded")
        case Left(messages) =>
          val errors = messages.justErrors
          errors must not be empty
          // The error about the incomplete type should be on line 2
          // (where "Currency" is), not line 3 (where "}" is)
          val errorOnLine2 = errors.exists(_.loc.line == 2)
          val errorOnLine3Only = errors.forall(_.loc.line == 3)
          if errorOnLine3Only then
            fail(
              s"Error incorrectly reported on line 3 instead of line 2: " +
                errors.map(m => s"${m.loc.line}:${m.loc.col} ${m.message}").mkString("; ")
            )
          end if
          errorOnLine2 mustBe true
    }
  }

  "NoWhiteSpaceParsers" should {
    "handle a URL" in { (td: TestData) =>
      val input = RiddlParserInput("https://www.wordnik.com/words/phi", td)
      parse[URL, URL](input, p => StringParser("").httpUrl(using p), identity) match {
        case Left(errors) =>
          fail(errors.format)
        case Right((actual, _)) =>
          actual mustBe URL("https://www.wordnik.com/words/phi")
      }
    }
  }
}
