/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.bast.BASTLoader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.TestData
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

class BASTLoaderTest extends AnyWordSpec {

  "BASTLoader" should {
    "load a BAST import and populate contents" in { (td: TestData) =>
      // Step 1: Create a simple RIDDL source with a type definition
      val sourceRiddl = """domain ImportedLib is {
                          |  type MyImportedType is String
                          |  briefly "A library domain"
                          |}
                          |""".stripMargin

      // Step 2: Parse the source RIDDL to get a Root
      val sourceInput = RiddlParserInput(sourceRiddl, "test-source")
      val sourceParseResult = TopLevelParser.parseInput(sourceInput, withVerboseFailures = true)

      sourceParseResult match {
        case Left(messages) =>
          fail(s"Source parse failed: ${messages.format}")

        case Right(sourceRoot: Root) =>
          // Step 3: Write the Root to a BAST file using the Pass framework
          val passInput = PassInput(sourceRoot)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          val bastBytes = output.bytes

          val tempDir = Files.createTempDirectory("bast-loader-test")
          val bastFile = tempDir.resolve("imported.bast")
          Files.write(bastFile, bastBytes)

          try {
            // Step 4: Create a RIDDL file that imports the BAST
            val riddlContent = s"""import "${bastFile.toAbsolutePath}"
                                  |
                                  |domain TestDomain is {
                                  |  briefly "A test domain"
                                  |}
                                  |""".stripMargin

            // Step 5: Parse the RIDDL file
            val rpi = RiddlParserInput(riddlContent, "test-import")
            val parseResult = TopLevelParser.parseInput(rpi, withVerboseFailures = true)

            parseResult match {
              case Left(messages) =>
                fail(s"Parse failed: ${messages.format}")

              case Right(parsedRoot: Root) =>
                // After parsing with the new integration, BASTLoader.loadImports
                // is automatically called. Verify the import was loaded.
                val imports = BASTLoader.getImports(parsedRoot)
                assert(imports.size == 1, s"Expected 1 import, got ${imports.size}")
                val bastImport = imports.head
                assert(bastImport.path.s.endsWith("imported.bast"),
                  s"Expected path ending with 'imported.bast', got '${bastImport.path.s}'")

                // With the new integration, contents should already be populated
                assert(bastImport.contents.nonEmpty, "BASTImport contents should be populated after parsing")

                // Verify we can find the imported domain in contents
                val importedDomains = bastImport.contents.toSeq.collect { case d: Domain => d }
                assert(importedDomains.size == 1, s"Expected 1 domain in import, got ${importedDomains.size}")
                assert(importedDomains.head.id.value == "ImportedLib",
                  s"Expected domain 'ImportedLib', got '${importedDomains.head.id.value}'")
            }
          } finally {
            // Cleanup
            Files.deleteIfExists(bastFile)
            Files.deleteIfExists(tempDir)
          }
      }
    }

    "report error for missing BAST file" in { (td: TestData) =>
      // Create a RIDDL file that imports a non-existent BAST
      val riddlContent = """import "nonexistent.bast"
                           |
                           |domain TestDomain is {
                           |  briefly "A test domain"
                           |}
                           |""".stripMargin

      val rpi = RiddlParserInput(riddlContent, "test-missing")
      val parseResult = TopLevelParser.parseInput(rpi, withVerboseFailures = true)

      // With the new integration, parse should return Left with error messages
      // because the missing BAST import will fail to load
      parseResult match {
        case Left(messages) =>
          // This is expected - the import failed
          assert(messages.hasErrors, "Should have error messages for missing import")

        case Right(parsedRoot: Root) =>
          // If it somehow succeeds, check that the import is empty
          val imports = BASTLoader.getImports(parsedRoot)
          if imports.nonEmpty then
            assert(imports.head.contents.isEmpty,
              "BASTImport should be empty if file wasn't found")
          end if
      }
    }

    "handle multiple imports" in { (td: TestData) =>
      // Create two RIDDL sources with different definitions
      val source1Riddl = """domain UtilsDomain is {
                           |  type TypeA is String
                           |  briefly "Utils domain"
                           |}
                           |""".stripMargin

      val source2Riddl = """domain ModelsDomain is {
                           |  type TypeB is Number
                           |  briefly "Models domain"
                           |}
                           |""".stripMargin

      // Parse both sources
      val input1 = RiddlParserInput(source1Riddl, "test-utils")
      val result1 = TopLevelParser.parseInput(input1, withVerboseFailures = true)

      val input2 = RiddlParserInput(source2Riddl, "test-models")
      val result2 = TopLevelParser.parseInput(input2, withVerboseFailures = true)

      (result1, result2) match {
        case (Right(root1: Root), Right(root2: Root)) =>
          // Write both to BAST files
          val passInput1 = PassInput(root1)
          val writerResult1 = Pass.runThesePasses(passInput1, Seq(BASTWriterPass.creator()))
          val output1 = writerResult1.outputOf[BASTOutput](BASTWriterPass.name).get

          val passInput2 = PassInput(root2)
          val writerResult2 = Pass.runThesePasses(passInput2, Seq(BASTWriterPass.creator()))
          val output2 = writerResult2.outputOf[BASTOutput](BASTWriterPass.name).get

          val tempDir = Files.createTempDirectory("bast-loader-test-multi")
          val bastFile1 = tempDir.resolve("utils.bast")
          val bastFile2 = tempDir.resolve("models.bast")
          Files.write(bastFile1, output1.bytes)
          Files.write(bastFile2, output2.bytes)

          try {
            val riddlContent = s"""import "${bastFile1.toAbsolutePath}"
                                  |import "${bastFile2.toAbsolutePath}"
                                  |
                                  |domain TestDomain is {
                                  |  briefly "A test domain"
                                  |}
                                  |""".stripMargin

            val rpi = RiddlParserInput(riddlContent, "test-multi-import")
            val parseResult = TopLevelParser.parseInput(rpi, withVerboseFailures = true)

            parseResult match {
              case Left(messages) =>
                fail(s"Parse failed: ${messages.format}")

              case Right(parsedRoot: Root) =>
                val imports = BASTLoader.getImports(parsedRoot)
                assert(imports.size == 2, s"Expected 2 imports, got ${imports.size}")

                // Verify both imports have contents (loaded automatically)
                imports.foreach { bi =>
                  assert(bi.contents.nonEmpty, s"Import ${bi.path.s} should have contents after loading")
                }

                // Verify we can find domains in the imports
                val allDomains = imports.flatMap(_.contents.toSeq.collect { case d: Domain => d })
                val domainNames = allDomains.map(_.id.value).toSet
                assert(domainNames.contains("UtilsDomain"), "Should find UtilsDomain in imports")
                assert(domainNames.contains("ModelsDomain"), "Should find ModelsDomain in imports")
            }
          } finally {
            Files.deleteIfExists(bastFile1)
            Files.deleteIfExists(bastFile2)
            Files.deleteIfExists(tempDir)
          }

        case _ =>
          fail("Failed to parse source RIDDL files")
      }
    }

    "load imports inside domains" in { (td: TestData) =>
      // Create a RIDDL source with shared types
      val sharedRiddl = """domain SharedTypes is {
                          |  type UserId is UUID
                          |  type Email is String
                          |  briefly "Shared type definitions"
                          |}
                          |""".stripMargin

      // Parse and convert to BAST
      val sharedInput = RiddlParserInput(sharedRiddl, "test-shared")
      val sharedResult = TopLevelParser.parseInput(sharedInput, withVerboseFailures = true)

      sharedResult match {
        case Left(messages) =>
          fail(s"Shared parse failed: ${messages.format}")

        case Right(sharedRoot: Root) =>
          // Write to BAST
          val passInput = PassInput(sharedRoot)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

          val tempDir = Files.createTempDirectory("bast-domain-import-test")
          val bastFile = tempDir.resolve("shared.bast")
          Files.write(bastFile, output.bytes)

          try {
            // Create a RIDDL file with import INSIDE a domain
            val riddlContent = s"""domain MyApp is {
                                  |  import "${bastFile.toAbsolutePath}"
                                  |
                                  |  context Users is {
                                  |    briefly "User management"
                                  |  }
                                  |  briefly "Main application domain"
                                  |}
                                  |""".stripMargin

            val rpi = RiddlParserInput(riddlContent, "test-domain-import")
            val parseResult = TopLevelParser.parseInput(rpi, withVerboseFailures = true)

            parseResult match {
              case Left(messages) =>
                fail(s"Parse failed: ${messages.format}")

              case Right(parsedRoot: Root) =>
                // Verify we can find the BASTImport inside the domain
                val imports = BASTLoader.getImports(parsedRoot)
                assert(imports.size == 1, s"Expected 1 import, got ${imports.size}")

                val bastImport = imports.head

                // Contents should be populated after parsing
                assert(bastImport.contents.nonEmpty, "BASTImport should have contents after loading")

                // Verify the SharedTypes domain was loaded
                val loadedDomains = bastImport.contents.toSeq.collect { case d: Domain => d }
                assert(loadedDomains.exists(_.id.value == "SharedTypes"),
                  "Should find SharedTypes domain in loaded contents")
            }
          } finally {
            Files.deleteIfExists(bastFile)
            Files.deleteIfExists(tempDir)
          }
      }
    }
  }
}
