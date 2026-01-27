/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.bast.HEADER_SIZE
import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class BastGenCommandTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  "BastifyCommand" should {
    "generate a BAST file from RIDDL input" in {
      // Create a temp directory with a copy of the test file
      val tempDir = Files.createTempDirectory("bastify-test")
      val tempInput = tempDir.resolve("test.riddl")
      val expectedOutput = tempDir.resolve("test.bast")

      try {
        // Copy test content to temp file (bastify outputs next to input)
        val riddlContent = """domain TestDomain is {
                             |  type MyType is String
                             |} with { briefly "test" }
                             |""".stripMargin
        Files.writeString(tempInput, riddlContent)

        val args = Array(
          "--quiet",
          "--show-missing-warnings=false",
          "--show-style-warnings=false",
          "bastify",
          tempInput.toString
        )

        val result = Commands.runMainForTest(args)
        result match {
          case Left(messages) =>
            fail(s"Command failed: ${messages.format}")
          case Right(_) =>
            // Verify the file was created next to input
            assert(Files.exists(expectedOutput), s"Output file $expectedOutput was not created")

            // Verify the file has valid BAST header
            val bytes = Files.readAllBytes(expectedOutput)
            assert(bytes.length > HEADER_SIZE, "File too small to be valid BAST")

            // Check magic bytes
            val magic = new String(bytes.take(4), "ASCII")
            assert(magic == "BAST", s"Invalid magic bytes: $magic")

            // Verify reasonable size
            assert(bytes.length > 100, s"BAST file suspiciously small: ${bytes.length} bytes")
        }
      } finally {
        Files.deleteIfExists(expectedOutput)
        Files.deleteIfExists(tempInput)
        Files.deleteIfExists(tempDir)
      }
    }

    "fail gracefully for non-existent input file" in {
      val args = Array(
        "--quiet",
        "bastify",
        "nonexistent-file.riddl"
      )

      val result = Commands.runMainForTest(args)
      result.isLeft mustBe true
    }
  }
}
