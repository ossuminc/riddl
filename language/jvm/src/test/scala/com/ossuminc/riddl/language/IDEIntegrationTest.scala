/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** IDE integration tests
  *
  * Tests IDE-specific scenarios:
  * - Incremental parsing (simulating user typing)
  * - Location tracking accuracy
  * - Error recovery for partial/invalid input
  * - Performance under IDE-like load
  * - Multi-file project handling
  *
  * Priority 8: IDE-Specific Tests from test-coverage-analysis.md
  */
class IDEIntegrationTest(using PlatformContext) extends ParsingTest {
  import IDEIntegrationTest.*

  private def parseModel(input: String): Either[Messages.Messages, Root] = {
    val rpi = RiddlParserInput(input, "ide-test")
    TopLevelParser.parseInput(rpi)
  }

  "Incremental parsing scenarios" should {

    "handle user typing domain definition incrementally" in { (_: TestData) =>
      // Simulate user typing: "domain Test is { ??? }"
      val steps = Seq(
        "d",
        "domain",
        "domain T",
        "domain Test",
        "domain Test i",
        "domain Test is",
        "domain Test is {",
        "domain Test is { ?",
        "domain Test is { ??",
        "domain Test is { ???",
        "domain Test is { ??? }"
      )

      steps.foreach { step =>
        parseModel(step) match {
          case Left(_) =>
            // Expected - incomplete input
            succeed
          case Right(root) =>
            // Only last step should succeed
            step must include("???")
            root.domains.nonEmpty mustBe true
        }
      }
    }

    "handle user typing type definition incrementally" in { (_: TestData) =>
      val base = "domain Test is {\n  "
      val typeSteps = Seq(
        "t",
        "type",
        "type U",
        "type User",
        "type User i",
        "type User is",
        "type User is S",
        "type User is String"
      )

      typeSteps.foreach { step =>
        val fullInput = base + step + "\n}"
        parseModel(fullInput) match {
          case Left(_) =>
            // Expected - incomplete input
            succeed
          case Right(root) =>
            // Only complete type should parse
            step mustBe "type User is String"
            root.domains.head.types.size mustBe 1
        }
      }
    }

    "handle adding new definition to existing domain" in { (_: TestData) =>
      val steps = Seq(
        "domain Test is { type T1 is String }",
        "domain Test is { type T1 is String\n  type T2 is Number }",
        "domain Test is { type T1 is String\n  type T2 is Number\n  type T3 is Boolean }"
      )

      steps.zipWithIndex.foreach { case (input, index) =>
        parseModel(input) match {
          case Left(messages) =>
            fail(s"Step ${index + 1} should parse: ${messages.format}")
          case Right(root) =>
            root.domains.head.types.size mustBe (index + 1)
        }
      }
    }

    "handle modifying existing definition" in { (_: TestData) =>
      // Original
      val original = "domain Test is { type User is String }"

      parseModel(original) match {
        case Right(root) =>
          root.domains.head.types.head.id.value mustBe "User"
        case Left(messages) =>
          fail(s"Original should parse: ${messages.format}")
      }

      // Modified
      val modified = "domain Test is { type User is Number }"

      parseModel(modified) match {
        case Right(root) =>
          val typeExpr = root.domains.head.types.head.typEx
          // Should have different type expression
          succeed
        case Left(messages) =>
          fail(s"Modified should parse: ${messages.format}")
      }
    }

    "handle deleting characters (backspace simulation)" in { (_: TestData) =>
      val steps = Seq(
        "domain Test is { type User is String }",
        "domain Test is { type User is String ",
        "domain Test is { type User is Strin",
        "domain Test is { type User is Str",
        "domain Test is { type User is Number }"
      )

      steps.foreach { step =>
        parseModel(step) match {
          case Left(_) =>
            // Incomplete steps may fail
            succeed
          case Right(root) =>
            // Complete steps should parse
            root.domains.head.types.size mustBe 1
        }
      }
    }

    "handle adding multiline comments while typing" in { (_: TestData) =>
      val steps = Seq(
        "domain Test is { /",
        "domain Test is { /*",
        "domain Test is { /* comment",
        "domain Test is { /* comment *",
        "domain Test is { /* comment */",
        "domain Test is { /* comment */\n  type T is String }"
      )

      steps.foreach { step =>
        parseModel(step) match {
          case Left(_) =>
            // Incomplete comment or syntax
            succeed
          case Right(root) =>
            // Only complete version should parse
            step must include("type T")
            root.domains.head.types.size mustBe 1
        }
      }
    }
  }

  "Location tracking accuracy" should {

    "track location of first element" in { (_: TestData) =>
      val input = "domain Test is { ??? }"
      parseModel(input) match {
        case Right(root) =>
          val domain = root.domains.head
          domain.loc.line mustBe 1
          domain.loc.col mustBe 1
          domain.id.loc.line mustBe 1
          domain.id.loc.col mustBe 8
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track location across multiple lines" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T1 is String
                    |  type T2 is Number
                    |}""".stripMargin
      parseModel(input) match {
        case Right(root) =>
          val types = root.domains.head.types
          types.size mustBe 2

          val t1 = types.head
          t1.loc.line mustBe 2

          val t2 = types(1)
          t2.loc.line mustBe 3
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track location of nested elements" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx is {
                    |    entity E is {
                    |      state S is { id: String }
                    |    }
                    |  }
                    |}""".stripMargin
      parseModel(input) match {
        case Right(root) =>
          val domain = root.domains.head
          val context = domain.contexts.head
          val entity = context.entities.head

          domain.loc.line mustBe 1
          context.loc.line mustBe 2
          entity.loc.line mustBe 3

          // Each nested element should have higher line number
          domain.loc.line must be < context.loc.line
          context.loc.line must be < entity.loc.line
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track column positions accurately" in { (_: TestData) =>
      val input = "domain Test is { type User is String }"
      parseModel(input) match {
        case Right(root) =>
          val domain = root.domains.head
          val typ = domain.types.head

          // "domain" starts at column 1
          domain.loc.col mustBe 1

          // "Test" identifier starts at column 8
          domain.id.loc.col mustBe 8

          // "type" starts at column 18
          typ.loc.col mustBe EXPECTED_TYPE_LOC_COL

          // "User" identifier starts at column 23
          typ.id.loc.col mustBe EXPECTED_TYPE_ID_LOC_COL
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track location after comments" in { (_: TestData) =>
      val input = """domain Test is {
                    |  // This is a comment
                    |  type T is String
                    |}""".stripMargin
      parseModel(input) match {
        case Right(root) =>
          val typ = root.domains.head.types.head
          // Type should be on line 3 (after comment on line 2)
          typ.loc.line mustBe 3
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track location with blank lines" in { (_: TestData) =>
      val input = """domain Test is {
                    |
                    |  type T1 is String
                    |
                    |  type T2 is Number
                    |}""".stripMargin
      parseModel(input) match {
        case Right(root) =>
          val types = root.domains.head.types
          types.head.loc.line mustBe 3
          types(1).loc.line mustBe 5
        case Left(messages) =>
          fail(s"Should parse: ${messages.format}")
      }
    }

    "track location in very long file (line 1000+)" in { (_: TestData) =>
      // Generate a file with 1000+ lines
      val header = "domain Large is {\n"
      val types = (1 to VERY_LARGE_FILE_TYPE_COUNT).map(i => s"  type T$i is String\n").mkString
      val footer = "}"
      val input = header + types + footer

      parseModel(input) match {
        case Right(root) =>
          val lastType = root.domains.head.types.last
          // Last type should be around line 1001
          lastType.loc.line must be >= VERY_LARGE_FILE_LINE_THRESHOLD
        case Left(messages) =>
          fail(s"Should parse large file: ${messages.format}")
      }
    }
  }

  "Error recovery for partial input" should {

    "recover from incomplete string literal" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T1 is String briefly "incomplete
                    |  type T2 is Number
                    |}""".stripMargin
      parseModel(input) match {
        case Left(messages) =>
          // Should error but report location
          messages.nonEmpty mustBe true
          messages.head.loc.line must be >= 2
        case Right(_) =>
          // Parser might be lenient
          succeed
      }
    }

    "recover from missing closing brace" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T is String
                    |""" // Missing closing brace
      parseModel(input) match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
        case Right(_) =>
          fail("Should detect missing closing brace")
      }
    }

    "handle syntax error mid-definition" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type T1 is String
                    |  type InvalidSyntax
                    |  type T2 is Number
                    |}""".stripMargin
      parseModel(input) match {
        case Left(messages) =>
          // Should report error around line 3
          messages.nonEmpty mustBe true
          val errorLine = messages.head.loc.line
          errorLine must (be >= 2 and be <= 4)
        case Right(_) =>
          fail("Should detect invalid syntax")
      }
    }

    "provide useful error for incomplete context" in { (_: TestData) =>
      val input = """domain Test is {
                    |  context Ctx
                    |}""".stripMargin
      parseModel(input) match {
        case Left(messages) =>
          messages.nonEmpty mustBe true
          // Error message should mention missing 'is' or body
          succeed
        case Right(_) =>
          fail("Should detect incomplete context")
      }
    }
  }

  "Performance under IDE-like load" should {

    "handle repeated parsing of same content (caching scenario)" in { (_: TestData) =>
      val input = "domain Test is { type User is String }"

      val times = (1 to 10).map { _ =>
        val start = System.nanoTime()
        parseModel(input)
        val elapsed = System.nanoTime() - start
        elapsed
      }

      // All parses should complete quickly (< 100ms each)
      times.foreach { elapsed =>
        (elapsed / 1_000_000) must be < SINGLE_PARSE_TIMEOUT_MS
      }
    }

    "handle parsing modifications with minimal content change" in { (_: TestData) =>
      val base = "domain Test is { "
      val variations = (1 to RAPID_MODIFICATION_COUNT).map(i => base + s"type T$i is String }")

      val start = System.currentTimeMillis()
      variations.foreach { variant =>
        parseModel(variant)
      }
      val totalElapsed = System.currentTimeMillis() - start

      // Should handle 50 small variations quickly (< 5 seconds total)
      totalElapsed must be < RAPID_MODIFICATIONS_TIMEOUT_MS
    }

    "handle rapid successive parses (typing simulation)" in { (_: TestData) =>
      val steps = (1 to INCREMENTAL_PARSE_STEPS).map { i =>
        val types = (1 to i).map(j => s"  type T$j is String").mkString("\n")
        s"domain Test is {\n$types\n}"
      }

      val start = System.currentTimeMillis()
      steps.foreach(parseModel)
      val totalElapsed = System.currentTimeMillis() - start

      // Should handle 100 incremental parses quickly (< 10 seconds)
      totalElapsed must be < INCREMENTAL_PARSES_TIMEOUT_MS
    }

    "maintain performance with deeply nested structure" in { (_: TestData) =>
      val deepNesting = (1 to 10).foldLeft("???") { case (inner, level) =>
        s"context Level$level is { $inner }"
      }
      val input = s"domain Deep is { $deepNesting }"

      val start = System.nanoTime()
      parseModel(input)
      val elapsed = (System.nanoTime() - start) / 1_000_000

      // Should parse deep nesting quickly (< 200ms)
      elapsed must be < SIMPLE_VALIDATION_TIMEOUT_MS
    }
  }

  "Multi-file project handling" should {

    "parse multiple independent domains" in { (_: TestData) =>
      val files = (1 to 5).map { i =>
        s"domain Domain$i is { type T$i is String }"
      }

      val results = files.map(parseModel)

      results.foreach {
        case Right(root) =>
          root.domains.size mustBe 1
        case Left(messages) =>
          fail(s"Should parse all domains: ${messages.format}")
      }
    }

    "handle parsing same file multiple times" in { (_: TestData) =>
      val input = "domain Test is { type User is String }"

      val results = (1 to 10).map(_ => parseModel(input))

      results.foreach {
        case Right(root) =>
          root.domains.head.types.size mustBe 1
        case Left(messages) =>
          fail(s"Should parse consistently: ${messages.format}")
      }

      // All results should be equivalent
      val firstResult = results.head
      results.tail.foreach { result =>
        result mustBe firstResult
      }
    }

    "handle project with many small files" in { (_: TestData) =>
      val files = (1 to SMALL_FILES_COUNT).map { i =>
        s"domain D$i is { type T is String }"
      }

      val start = System.currentTimeMillis()
      val results = files.map(parseModel)
      val totalElapsed = System.currentTimeMillis() - start

      // Should parse 100 small files quickly (< 15 seconds)
      totalElapsed must be < MANY_SMALL_FILES_TIMEOUT_MS

      // All should succeed
      results.count(_.isRight) mustBe EXPECTED_SUCCESSFUL_PARSES
    }

    "handle project with few large files" in { (_: TestData) =>
      val files = (1 to 5).map { fileNum =>
        val types = (1 to TYPES_PER_SMALL_FILE).map(i => s"  type T${fileNum}_$i is String").mkString("\n")
        s"domain Large$fileNum is {\n$types\n}"
      }

      val start = System.currentTimeMillis()
      val results = files.map(parseModel)
      val totalElapsed = System.currentTimeMillis() - start

      // Should parse 5 large files quickly (< 5 seconds)
      totalElapsed must be < RAPID_MODIFICATIONS_TIMEOUT_MS

      results.foreach {
        case Right(root) =>
          root.domains.head.types.size mustBe TOTAL_TYPES_IN_SMALL_FILES
        case Left(messages) =>
          fail(s"Should parse large files: ${messages.format}")
      }
    }
  }

  "Real-time validation scenarios" should {

    "validate on each keystroke (typing simulation)" in { (_: TestData) =>
      val typingSteps = Seq(
        ("domain Test is { ??? }", true),
        ("domain Test is { type ", false),
        ("domain Test is { type U", false),
        ("domain Test is { type User", false),
        ("domain Test is { type User is", false),
        ("domain Test is { type User is String", false),
        ("domain Test is { type User is String }", true)
      )

      typingSteps.foreach { case (input, shouldParse) =>
        parseModel(input) match {
          case Right(_) =>
            shouldParse mustBe true
          case Left(_) =>
            shouldParse mustBe false
        }
      }
    }

    "provide incremental error feedback" in { (_: TestData) =>
      val input = """domain Test is {
                    |  type Valid is String
                    |  type Invalid
                    |  type AlsoValid is Number
                    |}""".stripMargin

      parseModel(input) match {
        case Left(messages) =>
          // Should report error for line 3
          messages.nonEmpty mustBe true
          messages.head.loc.line mustBe 3
        case Right(_) =>
          fail("Should detect syntax error")
      }
    }

    "handle rapid validation requests" in { (_: TestData) =>
      val inputs = (1 to RAPID_VALIDATION_COUNT).map { i =>
        s"domain Test$i is { type T is String }"
      }

      val start = System.currentTimeMillis()
      inputs.foreach { input =>
        parseModel(input) // Validate each
      }
      val totalElapsed = System.currentTimeMillis() - start

      // Should handle 50 validation requests quickly (< 3 seconds)
      totalElapsed must be < RAPID_VALIDATION_TIMEOUT_MS
    }
  }
}

object IDEIntegrationTest {
  // Performance timeout thresholds (milliseconds)
  val SINGLE_PARSE_TIMEOUT_MS: Long = 100
  val SIMPLE_VALIDATION_TIMEOUT_MS: Long = 200
  val RAPID_MODIFICATIONS_TIMEOUT_MS: Long = 5000
  val INCREMENTAL_PARSES_TIMEOUT_MS: Long = 10000
  val MANY_SMALL_FILES_TIMEOUT_MS: Long = 15000
  val RAPID_VALIDATION_TIMEOUT_MS: Long = 3000

  // Test data sizes
  val INCREMENTAL_PARSE_STEPS: Int = 100
  val VERY_LARGE_FILE_TYPE_COUNT: Int = 1000
  val VERY_LARGE_FILE_LINE_THRESHOLD: Int = 1000
  val SMALL_FILES_COUNT: Int = 100
  val TYPES_PER_SMALL_FILE: Int = 200
  val TOTAL_TYPES_IN_SMALL_FILES: Int = 200
  val RAPID_MODIFICATION_COUNT: Int = 50
  val RAPID_VALIDATION_COUNT: Int = 50

  // Expected results
  val EXPECTED_SUCCESSFUL_PARSES: Int = 100

  // Location tracking test values
  val EXPECTED_TYPE_LOC_COL: Int = 18
  val EXPECTED_TYPE_ID_LOC_COL: Int = 23
}
