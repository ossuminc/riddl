/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.AST.{Root, Domain}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}

import scala.concurrent.duration.*

/** Standalone test runner to show detailed comparison output
  *
  * Run with: sbt "bast/Test/runMain com.ossuminc.riddl.bast.TestRunner"
  */
object TestRunner {

  def main(args: Array[String]): Unit = {
    println("\n" + "=" * 80)
    println("BAST Round-Trip Deep Comparison Test")
    println("=" * 80)

    testToDoodles()

    println("\n" + "=" * 80)
    println("Test Complete")
    println("=" * 80 + "\n")
  }

  private def testToDoodles(): Unit = {
    println("\n### Testing: ToDoodles Project ###")

    val url = URL.fromCwdPath("../riddl-examples/src/riddl/ToDoodles/ToDoodles.riddl")
    val inputFuture = RiddlParserInput.fromURL(url, "test-runner")

    Await.result(inputFuture.map { input =>
      // Step 1: Parse
      val parseResult = TopLevelParser.parseInput(input, true)
      parseResult match {
        case Right(originalRoot: Root) =>
          println(s"✓ Parsed successfully: ${originalRoot.contents.toSeq.size} top-level elements")

          // Debug: Print what's in the Root
          originalRoot.contents.toSeq.headOption.foreach { topLevel =>
            println(s"  Top-level type: ${topLevel.getClass.getSimpleName}")
            topLevel match {
              case d: Domain =>
                println(s"  Domain has ${d.contents.toSeq.size} contents:")
                d.contents.toSeq.take(5).foreach { item =>
                  println(s"    - ${item.getClass.getSimpleName}")
                }
              case _ =>
            }
          }

          // Step 2: Serialize
          val passInput = PassInput(originalRoot)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriter.name).get

          println(f"✓ BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)")
          println(f"  String table: ${output.stringTableSize}%,d strings")

          // Step 3: Deserialize
          BASTReader.read(output.bytes) match {
            case Right(reconstructedNebula) =>
              println(s"✓ BAST read: Nebula reconstructed")

              // Step 4: Deep comparison
              println(s"\n=== Deep Structural Comparison ===")
              println(s"Original: Root with ${originalRoot.contents.toSeq.size} top-level elements")
              println(s"Reconstructed: Nebula with ${reconstructedNebula.contents.toSeq.size} top-level elements")

              val results = DeepASTComparison.compareRootAndNebula(originalRoot, reconstructedNebula)
              val report = DeepASTComparison.report(results)
              println(report)

              val allSucceeded = results.forall(_.isSuccess)
              if allSucceeded then
                println("\n✓✓✓ COMPLETE SUCCESS: Full structural reflectivity verified ✓✓✓")
                println("    AST → BAST → AST preserves all data at all hierarchy levels")
              else
                println("\n✗✗✗ FAILURES DETECTED ✗✗✗")
                println("    See failure details above")
              end if

              allSucceeded

            case Left(errors) =>
              println(s"✗ Deserialization failed: ${errors.format}")
              false
          }

        case Left(messages) =>
          println(s"✗ Parse failed: ${messages.format}")
          false
      }
    }, 30.seconds)
  }
}
