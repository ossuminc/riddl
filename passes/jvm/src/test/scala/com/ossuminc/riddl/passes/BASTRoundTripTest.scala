/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Root, Nebula}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.TestData
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Round-trip tests for BAST serialization/deserialization
  *
  * These tests verify that: RIDDL text -> AST -> BAST binary -> AST produces an equivalent AST
  *
  * This is the CRITICAL test for Phase 2 completion.
  */
class BASTRoundTripTest extends AnyWordSpec {

  "BAST Round Trip" should {

    "serialize and deserialize simple domain" in { (td: TestData) =>
      val riddlSource = """domain TestDomain is {
                          |  type MyType is String
                          |  briefly "A test domain"
                          |}
                          |""".stripMargin

      val input = RiddlParserInput(riddlSource, "test-simple")
      val parseResult = TopLevelParser.parseInput(input, true)

      parseResult match {
        case Right(originalRoot: Root) =>
          println(s"\n=== Round Trip Test: simple domain ===")

          // Serialize AST -> BAST binary
          val passInput = PassInput(originalRoot)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

          println(f"BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)")

          // Deserialize BAST binary -> AST
          BASTReader.read(output.bytes) match {
            case Right(reconstructedNebula) =>
              println(s"BAST read: Nebula reconstructed")

              // Compare original and reconstructed
              val areEqual = compareRoots(originalRoot, reconstructedNebula)

              if areEqual then
                println("[OK] Round trip successful: Original AST == Reconstructed AST")
              else
                println("[FAIL] Round trip FAILED: ASTs differ")
              end if

              assert(areEqual, "Round trip test failed: ASTs are not equivalent")

            case Left(errors) =>
              fail(s"Deserialization failed: ${errors.format}")
          }

        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize everything.riddl" in { (td: TestData) =>
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(inputFuture.map { input =>
        // Step 1: Parse RIDDL text -> AST
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(originalRoot: Root) =>
            println(s"\n=== Round Trip Test: everything.riddl ===")
            println(s"Original AST parsed successfully")

            // Step 2: Serialize AST -> BAST binary
            val passInput = PassInput(originalRoot)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

            println(f"BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)")

            // Step 3: Deserialize BAST binary -> AST
            BASTReader.read(output.bytes) match {
              case Right(reconstructedNebula) =>
                println(s"BAST read: Nebula reconstructed")

                // Step 4: Compare original and reconstructed
                val areEqual = compareRoots(originalRoot, reconstructedNebula)

                if areEqual then
                  println("[OK] Round trip successful: Original AST == Reconstructed AST")
                else
                  println("[FAIL] Round trip FAILED: ASTs differ")
                end if

                areEqual

              case Left(errors) =>
                println(s"[FAIL] Deserialization failed: ${errors.format}")
                false
            }

          case Left(messages) =>
            println(s"Parse failed: ${messages.format}")
            false
        }
      }, 30.seconds)

      assert(result, "Round trip test failed: ASTs are not equivalent")
    }
  }

  /** Compare Root (original) with Nebula (reconstructed) for deep structural equality
    *
    * Note: BASTWriter writes Root using NODE_NEBULA tag, so deserialization produces Nebula.
    * This is expected - we're comparing the CONTENT, not the container type.
    *
    * Uses DeepASTComparison to recursively verify all fields, identifiers, locations, and nested content.
    */
  private def compareRoots(original: Root, reconstructed: Nebula): Boolean = {
    println(s"\n=== Deep Structural Comparison ===")
    println(s"Original: Root with ${original.contents.toSeq.size} top-level elements")
    println(s"Reconstructed: Nebula with ${reconstructed.contents.toSeq.size} top-level elements")

    // Perform deep comparison
    val results = DeepASTComparison.compareRootAndNebula(original, reconstructed)

    // Generate report
    val report = DeepASTComparison.report(results)
    println(report)

    // Check if all comparisons succeeded
    val allSucceeded = results.forall(_.isSuccess)

    if allSucceeded then
      println("[OK] Complete structural reflectivity verified: AST -> BAST -> AST preserves all data")
    else
      println("[FAIL] Structural differences detected - see failures above")
    end if

    allSucceeded
  }
}
