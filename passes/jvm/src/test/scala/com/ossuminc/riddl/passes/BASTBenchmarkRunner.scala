/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Nebula, Root}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Standalone benchmark runner for BAST performance
  *
  * Run this directly to see detailed performance output:
  *   sbt "project passes" "Test/runMain com.ossuminc.riddl.passes.BASTBenchmarkRunner"
  */
object BASTBenchmarkRunner {

  def main(args: Array[String]): Unit = {
    println("\n" + "=" * 80)
    println("BAST Performance Benchmark: Parse vs Load")
    println("=" * 80)

    val testFile = "language/input/dokn.riddl"

    if !Files.exists(Paths.get(testFile)) then
      println(s"ERROR: Test file not found: $testFile")
      return
    end if

    // Load the test file - use same pattern as round trip test
    val url = URL.fromCwdPath(testFile)
    val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

    val result = Await.result(inputFuture.map { input =>
      println(s"\nSource file: ${input.origin}")
      println(s"Source size: ${input.data.length} characters")

      // Step 1: Parse RIDDL (baseline) - use verbose failures like test
      println("\n--- Phase 1: Parse RIDDL (text → AST) ---")
      val parseStart = System.nanoTime()
      val parseResult = TopLevelParser.parseInput(input, true) // true for verbose
      val parseTime = (System.nanoTime() - parseStart) / 1_000_000.0

      parseResult match {
        case Right(root: Root) =>
          println(f"Parse time: $parseTime%.2f ms")

          // Step 2: Serialize to BAST
          println("\n--- Phase 2: Serialize to BAST (AST → binary) ---")
          val writeStart = System.nanoTime()
          val passInput = PassInput(root)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val writeTime = (System.nanoTime() - writeStart) / 1_000_000.0

          val bastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          val bastBytes = bastOutput.bytes

          println(f"Write time: $writeTime%.2f ms")
          println(f"BAST size: ${bastBytes.length}%,d bytes (${bastBytes.length / 1024}%,d KB)")
          println(f"Nodes: ${bastOutput.nodeCount}%,d")
          println(f"String table: ${bastOutput.stringTableSize}%,d strings")

          // Step 3: Load from BAST
          println("\n--- Phase 3: Load from BAST (binary → AST) ---")

          // Debug: dump bytes around position 1370
          println("\n=== Bytes around position 1370 (hex) ===")
          val start = 1350
          val end = math.min(1400, bastBytes.length)
          bastBytes.slice(start, end).grouped(16).zipWithIndex.foreach { case (group, i) =>
            val hex = group.map(b => f"${b & 0xFF}%02X").mkString(" ")
            println(f"${start + i * 16}%04X: $hex")
          }
          println()

          val loadStart = System.nanoTime()
          val loadResult = BASTReader.read(bastBytes)
          val loadTime = (System.nanoTime() - loadStart) / 1_000_000.0

          loadResult match {
            case Right(nebula: Nebula) =>
              println(f"Load time: $loadTime%.2f ms")
              println(f"Loaded ${nebula.contents.toSeq.size} root items")

              // Summary
              val speedup = parseTime / loadTime

              println("\n" + "=" * 80)
              println("SUMMARY")
              println("=" * 80)
              println(f"Parse time (text → AST):  $parseTime%8.2f ms")
              println(f"Load time (binary → AST): $loadTime%8.2f ms")
              println(f"Speedup:                  $speedup%8.1fx")
              println()

              if speedup >= 10.0 then
                println(s"✓ EXCELLENT: ${speedup.toInt}x speedup exceeds 10x target!")
              else if speedup >= 2.0 then
                println(s"✓ GOOD: ${speedup.toInt}x speedup is meaningful")
              else if speedup > 1.0 then
                println(s"⚠ MARGINAL: ${speedup.toInt}x speedup is minimal")
              else
                println(s"✗ NO BENEFIT: Load is not faster than parse")
              end if

              // Multi-iteration benchmark
              benchmarkIterations(input, bastBytes)

            case Left(errors) =>
              println(s"BAST load FAILED: ${errors.map(_.format).mkString("\n")}")
          }

        case Left(messages) =>
          println(s"Parse FAILED: ${messages.format}")
      }
    }, 60.seconds)

    println("\n" + "=" * 80)
    println("Benchmark Complete")
    println("=" * 80 + "\n")
  }

  private def benchmarkIterations(input: RiddlParserInput, bastBytes: Array[Byte]): Unit = {
    val iterations = 50

    println(s"\n--- Multi-Iteration Benchmark ($iterations iterations) ---")

    // Warmup
    for _ <- 0 until 5 do
      val rpi = RiddlParserInput(input.data, input.root)
      TopLevelParser.parseInput(rpi, false)
      BASTReader.read(bastBytes)
    end for

    // Measure parse times
    val parseTimes = new Array[Long](iterations)
    for i <- 0 until iterations do
      val rpi = RiddlParserInput(input.data, input.root)
      val start = System.nanoTime()
      TopLevelParser.parseInput(rpi, false)
      parseTimes(i) = System.nanoTime() - start
    end for

    // Measure load times
    val loadTimes = new Array[Long](iterations)
    for i <- 0 until iterations do
      val start = System.nanoTime()
      BASTReader.read(bastBytes)
      loadTimes(i) = System.nanoTime() - start
    end for

    val avgParseMs = parseTimes.sum / iterations / 1_000_000.0
    val avgLoadMs = loadTimes.sum / iterations / 1_000_000.0
    val minParseMs = parseTimes.min / 1_000_000.0
    val minLoadMs = loadTimes.min / 1_000_000.0
    val maxParseMs = parseTimes.max / 1_000_000.0
    val maxLoadMs = loadTimes.max / 1_000_000.0

    val avgSpeedup = avgParseMs / avgLoadMs

    println()
    println("Parse (text → AST):")
    println(f"  Average: $avgParseMs%8.2f ms")
    println(f"  Min:     $minParseMs%8.2f ms")
    println(f"  Max:     $maxParseMs%8.2f ms")
    println()
    println("Load (binary → AST):")
    println(f"  Average: $avgLoadMs%8.2f ms")
    println(f"  Min:     $minLoadMs%8.2f ms")
    println(f"  Max:     $maxLoadMs%8.2f ms")
    println()
    println(f"Average speedup: $avgSpeedup%.1fx")
  }
}
