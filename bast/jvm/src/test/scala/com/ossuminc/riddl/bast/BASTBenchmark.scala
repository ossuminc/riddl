/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Standalone benchmark for BAST serialization performance
  *
  * IMPORTANT: This benchmark is INCOMPLETE and measures the WRONG comparison.
  *
  * Current measurements:
  *   - Parse time: text → AST
  *   - BAST write time: AST → binary
  *
  * What we ACTUALLY need to measure (once BASTReader exists):
  *   - Parse time: text → AST  (baseline)
  *   - BAST load time: binary → AST  (optimized approach)
  *
  * The critical comparison is:
  *   text → AST  vs  binary → AST
  *
  * BAST loading (binary → AST) MUST be >10x faster than parsing (text → AST)
  * or the optimization is pointless.
  *
  * See: bast/jvm/src/test/resources/BENCHMARK_NOTES.md for details
  *
  * Run this as a main class to see detailed output
  */
object BASTBenchmark {

  def main(args: Array[String]): Unit = {
    println("\n" + "=" * 80)
    println("BAST Serialization Performance Benchmark (INCOMPLETE)")
    println("=" * 80)
    println("NOTE: This measures AST→binary write time, not binary→AST load time")
    println("      Real performance comparison requires BASTReader implementation")
    println("=" * 80)

    // Test 1: Comprehensive test file
    benchmarkComprehensive()

    // Test 2: ToDoodles (if available)
    benchmarkToDoodles()

    // Test 3: ReactiveBBQ (if available)
    benchmarkReactiveBBQ()

    // Test 4: Throughput benchmark
    benchmarkThroughput()

    println("\n" + "=" * 80)
    println("Benchmark Complete")
    println("=" * 80 + "\n")
  }

  private def benchmarkComprehensive(): Unit = {
    println("\n### Comprehensive Test File ###")
    val url = URL.fromCwdPath("bast/jvm/src/test/resources/comprehensive-test.riddl")
    val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

    Await.result(inputFuture.map { input =>
      val parseResult = TopLevelParser.parseInput(input, true)
      parseResult match {
        case Right(root: Root) =>
          val passInput = PassInput(root)
          val result = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
          val output = result.outputOf[BASTOutput](BASTWriter.name).get

          println(f"  Nodes:        ${output.nodeCount}%,8d")
          println(f"  Size:         ${output.bytes.length}%,8d bytes (${output.bytes.length / 1024}%,d KB)")
          println(f"  String table: ${output.stringTableSize}%,8d strings")

        case Left(messages) =>
          println(s"  ERROR: Parse failed: ${messages.format}")
      }
    }, 30.seconds)
  }

  private def benchmarkToDoodles(): Unit = {
    val examplesPath = Paths.get("/Users/reid/Code/ossuminc/riddl-examples")
    if !Files.exists(examplesPath) then
      println("\n### ToDoodles Project ###")
      println("  SKIPPED: riddl-examples not found")
    else
      println("\n### ToDoodles Project ###")
      val url = URL.fromCwdPath("../riddl-examples/src/riddl/ToDoodles/ToDoodles.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

      Await.result(inputFuture.map { input =>
        val parseStart = System.nanoTime()
        val parseResult = TopLevelParser.parseInput(input, true)
        val parseTime = (System.nanoTime() - parseStart) / 1_000_000.0

        parseResult match {
          case Right(root: Root) =>
            val bastStart = System.nanoTime()
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
            val bastTime = (System.nanoTime() - bastStart) / 1_000_000.0

            val output = result.outputOf[BASTOutput](BASTWriter.name).get

            println(f"  Parse time (text→AST):      $parseTime%8.2f ms")
            println(f"  BAST write time (AST→bin):  $bastTime%8.2f ms")
            println(f"  Write/Parse ratio:          ${bastTime / parseTime}%8.2fx")
            println(f"  TODO: BAST load (bin→AST):  ??? ms (NEEDS BASTReader)")
            println(f"  TODO: Load/Parse speedup:   ??? x (TARGET: >10x)")
            println(f"  Nodes:             ${output.nodeCount}%,8d")
            println(f"  Size:              ${output.bytes.length}%,8d bytes (${output.bytes.length / 1024}%,d KB)")
            println(f"  String table:      ${output.stringTableSize}%,8d strings")

          case Left(messages) =>
            println(s"  ERROR: Parse failed: ${messages.format}")
        }
      }, 30.seconds)
  }

  private def benchmarkReactiveBBQ(): Unit = {
    val examplesPath = Paths.get("/Users/reid/Code/ossuminc/riddl-examples")
    if !Files.exists(examplesPath) then
      println("\n### ReactiveBBQ Restaurant Domain ###")
      println("  SKIPPED: riddl-examples not found")
    else
      println("\n### ReactiveBBQ Restaurant Domain ###")
      val url = URL.fromCwdPath("../riddl-examples/src/riddl/ReactiveBBQ/restaurant/domain.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

      Await.result(inputFuture.map { input =>
        val parseStart = System.nanoTime()
        val parseResult = TopLevelParser.parseInput(input, true)
        val parseTime = (System.nanoTime() - parseStart) / 1_000_000.0

        parseResult match {
          case Right(root: Root) =>
            val bastStart = System.nanoTime()
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
            val bastTime = (System.nanoTime() - bastStart) / 1_000_000.0

            val output = result.outputOf[BASTOutput](BASTWriter.name).get

            println(f"  Parse time (text→AST):      $parseTime%8.2f ms")
            println(f"  BAST write time (AST→bin):  $bastTime%8.2f ms")
            println(f"  Write/Parse ratio:          ${bastTime / parseTime}%8.2fx")
            println(f"  TODO: BAST load (bin→AST):  ??? ms (NEEDS BASTReader)")
            println(f"  TODO: Load/Parse speedup:   ??? x (TARGET: >10x)")
            println(f"  Nodes:             ${output.nodeCount}%,8d")
            println(f"  Size:              ${output.bytes.length}%,8d bytes (${output.bytes.length / 1024}%,d KB)")
            println(f"  String table:      ${output.stringTableSize}%,d strings")

          case Left(messages) =>
            println(s"  ERROR: Parse failed: ${messages.format}")
        }
      }, 30.seconds)
  }

  private def benchmarkThroughput(): Unit = {
    println("\n### Throughput Benchmark (100 iterations) ###")
    val url = URL.fromCwdPath("bast/jvm/src/test/resources/comprehensive-test.riddl")
    val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

    Await.result(inputFuture.map { input =>
      val parseResult = TopLevelParser.parseInput(input, true)
      parseResult match {
        case Right(root: Root) =>
          val iterations = 100
          val times = new Array[Long](iterations)

          // Warmup
          for _ <- 0 until 10 do
            val passInput = PassInput(root)
            Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
          end for

          // Measure
          var lastResult = null: com.ossuminc.riddl.passes.PassesResult
          for i <- 0 until iterations do
            val start = System.nanoTime()
            val passInput = PassInput(root)
            lastResult = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
            val end = System.nanoTime()
            times(i) = end - start
          end for

          val avgNanos = times.sum / iterations
          val avgMillis = avgNanos / 1_000_000.0
          val minMillis = times.min / 1_000_000.0
          val maxMillis = times.max / 1_000_000.0

          val output = lastResult.outputOf[BASTOutput](BASTWriter.name).get
          val bytesPerSec = (output.bytes.length / avgMillis * 1000.0).toLong
          val nodesPerSec = (output.nodeCount / avgMillis * 1000.0).toLong

          println(f"  Average time:  $avgMillis%8.2f ms")
          println(f"  Min time:      $minMillis%8.2f ms")
          println(f"  Max time:      $maxMillis%8.2f ms")
          println(f"  Throughput:    ${bytesPerSec / 1024}%,8d KB/s")
          println(f"  Node rate:     $nodesPerSec%,8d nodes/s")

        case Left(messages) =>
          println(s"  ERROR: Parse failed: ${messages.format}")
      }
    }, 60.seconds)
  }
}
