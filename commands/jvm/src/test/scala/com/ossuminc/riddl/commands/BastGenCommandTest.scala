/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.bast.{BASTReader, BASTOutput, HEADER_SIZE}
import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class BastGenCommandTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  // Use a simple inline RIDDL string via the test infrastructure
  val simpleInputFile = "language/input/everything.riddl"

  "BastGenCommand" should {
    "generate a BAST file from RIDDL input" in {
      val tempDir = Files.createTempDirectory("bast-gen-test")
      val outputFile = tempDir.resolve("output.bast")

      try {
        val args = Array(
          "--quiet",
          "--show-missing-warnings=false",
          "--show-style-warnings=false",
          "bast-gen",
          simpleInputFile,
          "-o", outputFile.toString
        )

        val result = Commands.runMainForTest(args)
        result match {
          case Left(messages) =>
            fail(s"Command failed: ${messages.format}")
          case Right(_) =>
            // Verify the file was created
            assert(Files.exists(outputFile), s"Output file $outputFile was not created")

            // Verify the file has valid BAST header
            val bytes = Files.readAllBytes(outputFile)
            assert(bytes.length > HEADER_SIZE, "File too small to be valid BAST")

            // Check magic bytes
            val magic = new String(bytes.take(4), "ASCII")
            assert(magic == "BAST", s"Invalid magic bytes: $magic")

            // Verify reasonable size
            assert(bytes.length > 100, s"BAST file suspiciously small: ${bytes.length} bytes")
        }
      } finally {
        Files.deleteIfExists(outputFile)
        Files.deleteIfExists(tempDir)
      }
    }

    "use default output filename based on input" in {
      val tempDir = Files.createTempDirectory("bast-gen-test-default")
      val expectedOutput = tempDir.resolve("everything.bast")

      try {
        val args = Array(
          "--quiet",
          "--show-missing-warnings=false",
          "--show-style-warnings=false",
          "bast-gen",
          simpleInputFile,
          "--output-dir", tempDir.toString
        )

        val result = Commands.runMainForTest(args)
        result match {
          case Left(messages) =>
            fail(s"Command failed: ${messages.format}")
          case Right(_) =>
            assert(Files.exists(expectedOutput),
              s"Expected output file $expectedOutput was not created")
        }
      } finally {
        Files.deleteIfExists(expectedOutput)
        Files.deleteIfExists(tempDir)
      }
    }

    "fail gracefully for non-existent input file" in {
      val args = Array(
        "--quiet",
        "bast-gen",
        "nonexistent-file.riddl"
      )

      val result = Commands.runMainForTest(args)
      result.isLeft mustBe true
    }
  }
}
