/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class UnbastifyCommandTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  "UnbastifyCommand" should {
    "round-trip RIDDL through bastify and unbastify" in {
      val tempDir = Files.createTempDirectory("unbastify-test")
      val tempInput = tempDir.resolve("test.riddl")
      val bastFile = tempDir.resolve("test.bast")
      val outputDir = tempDir.resolve("output")

      try {
        // Step 1: Write a RIDDL source file
        val riddlContent = """domain TestDomain is {
                             |  type MyType is String
                             |} with { briefly "test" }
                             |""".stripMargin
        Files.writeString(tempInput, riddlContent)

        // Step 2: Run bastify to produce a .bast file
        val bastifyArgs = Array(
          "--quiet",
          "--show-missing-warnings=false",
          "--show-style-warnings=false",
          "bastify",
          tempInput.toString
        )

        val bastifyResult = Commands.runMainForTest(bastifyArgs)
        bastifyResult match {
          case Left(messages) =>
            fail(s"bastify failed: ${messages.format}")
          case Right(_) =>
            assert(Files.exists(bastFile), s"BAST file $bastFile was not created")
        }

        // Step 3: Run unbastify on the .bast file
        val unbastifyArgs = Array(
          "--quiet",
          "--show-missing-warnings=false",
          "--show-style-warnings=false",
          "unbastify",
          bastFile.toString,
          "-o",
          outputDir.toString
        )

        val unbastifyResult = Commands.runMainForTest(unbastifyArgs)
        unbastifyResult match {
          case Left(messages) =>
            fail(s"unbastify failed: ${messages.format}")
          case Right(_) =>
            // Step 4: Verify output directory contains .riddl file(s)
            assert(Files.exists(outputDir), s"Output directory $outputDir was not created")

            val riddlFiles = Files.list(outputDir).toArray.map(_.asInstanceOf[Path])
              .filter(_.toString.endsWith(".riddl"))
            assert(riddlFiles.nonEmpty, "No .riddl files generated in output directory")

            // Step 5: Verify output content contains expected domain definition
            val outputContent = riddlFiles.map(p => Files.readString(p)).mkString("\n")
            assert(outputContent.contains("TestDomain"), s"Output does not contain 'TestDomain': $outputContent")
        }
      } finally {
        // Clean up
        def deleteRecursively(path: Path): Unit = {
          if Files.isDirectory(path) then
            Files.list(path).forEach(p => deleteRecursively(p.asInstanceOf[Path]))
          Files.deleteIfExists(path)
        }
        deleteRecursively(tempDir)
      }
    }

    "fail gracefully for non-existent input file" in {
      val args = Array(
        "--quiet",
        "unbastify",
        "nonexistent-file.bast"
      )

      val result = Commands.runMainForTest(args)
      result.isLeft mustBe true
    }
  }
}
