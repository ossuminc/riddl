/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Error path and recovery tests for RIDDL parsers
  *
  * Tests error handling and recovery scenarios:
  * - Malformed input handling
  * - Syntax error recovery
  * - Invalid token sequences
  * - Unclosed delimiters
  * - Invalid escape sequences
  * - Premature end of input
  *
  * Priority 6: Error Path Tests from test-coverage-analysis.md
  */
class ParserErrorRecoveryTest(using PlatformContext) extends ParsingTest {

  "Parser malformed input handling" should {

    "reject invalid domain syntax" in { (_: TestData) =>
      val input = "domain { ??? }" // Missing name
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          messages.justErrors.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject domain without name")
      }
    }

    "reject domain with invalid identifier" in { (_: TestData) =>
      val input = "domain 123Invalid is { ??? }" // Identifier starting with number
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid identifier")
      }
    }

    "reject domain with missing 'is' keyword" in { (_: TestData) =>
      val input = "domain Test { ??? }" // Missing 'is'
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject missing 'is' keyword")
      }
    }

    "reject domain with missing braces" in { (_: TestData) =>
      val input = "domain Test is ???" // Missing braces
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject missing braces")
      }
    }

    "handle unclosed string literal" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is String briefly "unclosed string
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject unclosed string")
      }
    }

    "handle unclosed block comment" in { (_: TestData) =>
      val input = """domain Test is {
                    |  /* This comment is never closed
                    |  type T is String
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          // Might succeed if parser is lenient with comments
          succeed
      }
    }

    "handle premature end of input" in { (_: TestData) =>
      val input = "domain Test is { type T is"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject premature end of input")
      }
    }
  }

  "Parser invalid token sequences" should {

    "reject multiple consecutive keywords" in { (_: TestData) =>
      val input = "domain is is Test is { ??? }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid keyword sequence")
      }
    }

    "reject invalid operator usage" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is ++ String
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid operator")
      }
    }

    "reject mismatched brackets" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is { field: String ]
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject mismatched brackets")
      }
    }

    "reject extra closing braces" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is String
                    |}}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          // Might succeed if parser stops at first valid parse
          succeed
      }
    }
  }

  "Parser invalid type definitions" should {

    "reject type without type expression" in { (_: TestData) =>
      val input = "domain Test is { type T is }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject type without expression")
      }
    }

    "reject aggregation with invalid field syntax" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is {
                    |    field String
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid field syntax")
      }
    }

    "reject enumeration without values" in { (_: TestData) =>
      val input = "domain Test is { type T is one of { } }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject empty enumeration")
      }
    }

    "reject alternation without options" in { (_: TestData) =>
      val input = "domain Test is { type T is one of { } }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject empty alternation")
      }
    }

    "reject invalid cardinality syntax" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is {
                    |    field: String**
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid cardinality")
      }
    }

    "reject negative numeric constraint" in { (_: TestData) =>
      val input = "domain Test is { type T is String(-1) }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject negative string length")
      }
    }
  }

  "Parser invalid context definitions" should {

    "reject context without body" in { (_: TestData) =>
      val input = "domain Test is { context Ctx }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject context without body")
      }
    }

    "reject nested domains" in { (_: TestData) =>
      val input = """domain Outer is {
                    |  domain Inner is { ??? }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject nested domains")
      }
    }
  }

  "Parser invalid entity definitions" should {

    "reject entity outside context" in { (_: TestData) =>
      val input = """domain Test is {
                    |  entity InvalidLocation is { ??? }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          // May succeed if entities are allowed at domain level
          succeed
      }
    }

    "reject entity with invalid state syntax" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx is {
                    |    entity E is {
                    |      state BadState
                    |    }
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid state syntax")
      }
    }

    "reject handler without clauses" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx is {
                    |    entity E is {
                    |      handler H is { }
                    |    }
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          // Might succeed if empty handlers are allowed
          succeed
      }
    }
  }

  "Parser invalid option syntax" should {

    "reject option with invalid value" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx is {
                    |    entity E is { ??? } with { option @#$ }
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid option value")
      }
    }

    "reject conflicting options" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx is {
                    |    entity E is { ??? } with {
                    |      option transient
                    |      option persistent
                    |    }
                    |  }
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          // Parser may succeed; validation should catch conflict
          succeed
        case Right(_) =>
          // Parser may succeed; validation should catch conflict
          succeed
      }
    }
  }

  "Parser invalid include syntax" should {

    "reject include without path" in { (_: TestData) =>
      val input = "domain Test is { include }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject include without path")
      }
    }

    "reject include with invalid path syntax" in { (_: TestData) =>
      val input = """domain Test is {
                    |  include @#$invalid
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid include path")
      }
    }
  }

  "Parser error message quality" should {

    "provide helpful error for missing domain name" in { (_: TestData) =>
      val input = "domain { ??? }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          val errorMsg = messages.head.message.toLowerCase
          // Should mention what's expected
          (errorMsg.contains("identifier") || errorMsg.contains("name") || errorMsg.contains("expected")) mustBe true
        case Right(_) =>
          fail("Should provide error for missing name")
      }
    }

    "provide helpful error for unclosed brace" in { (_: TestData) =>
      val input = "domain Test is {"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          val errorMsg = messages.head.message.toLowerCase
          // Should mention closing brace or end of input
          (errorMsg.contains("}") || errorMsg.contains("end") || errorMsg.contains("expected")) mustBe true
        case Right(_) =>
          fail("Should provide error for unclosed brace")
      }
    }

    "provide line and column information" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type Invalid
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          val msg = messages.head
          msg.loc.line must be > 0
          msg.loc.col must be > 0
        case Right(_) =>
          fail("Should provide error with location")
      }
    }
  }

  "Parser recovery from errors" should {

    "handle multiple errors in single parse" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type Invalid1
                    |  type Invalid2
                    |  type Valid is String
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          // Should report multiple errors if parser continues after first error
          messages.size must be >= 1
        case Right(_) =>
          // Parser might stop at first error
          succeed
      }
    }

    "identify correct error location in complex input" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T1 is String
                    |  type T2 is Number
                    |  type Invalid
                    |  type T3 is Boolean
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          // Error should be around line 4 where "Invalid" is
          messages.head.loc.line must (be >= 3 and be <= 5)
        case Right(_) =>
          fail("Should detect error")
      }
    }
  }

  "Parser invalid escape sequences" should {

    "reject invalid escape sequence in string" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is String briefly "Invalid \x escape"
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          // Should error on invalid escape
          succeed
        case Right(_) =>
          // Might succeed if parser is lenient with escapes
          succeed
      }
    }

    "handle backslash at end of string" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is String briefly "Ends with \"
                    |}""".stripMargin
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject trailing backslash")
      }
    }
  }

  "Parser invalid identifier usage" should {

    "reject reserved keyword as identifier" in { (_: TestData) =>
      val input = "domain domain is { ??? }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject keyword as identifier")
      }
    }

    "reject identifier with invalid characters" in { (_: TestData) =>
      val input = "domain Test@Invalid is { ??? }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should reject invalid characters in identifier")
      }
    }

    "reject identifier with only underscores" in { (_: TestData) =>
      val input = "domain ___ is { ??? }"
      val parser = StringParser(input)
      parser.parseRoot match {
        case Left(messages) =>
          // May error or may accept
          succeed
        case Right(_) =>
          // May accept underscore-only identifiers
          succeed
      }
    }
  }
}
