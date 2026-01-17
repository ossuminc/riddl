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
  *
  * The benchmark tests three file sizes:
  *   - small.riddl:  ~60 lines, 2 contexts
  *   - medium.riddl: ~370 lines, 7 contexts
  *   - large.riddl:  ~1450 lines, 10 contexts (enterprise system)
  */
object BASTBenchmarkRunner {

  /** Test file configuration */
  case class TestFile(name: String, path: String, description: String)

  val testFiles: Seq[TestFile] = Seq(
    TestFile("small", "testkit/jvm/src/test/resources/performance/small.riddl", "~60 lines, 2 contexts"),
    TestFile("medium", "testkit/jvm/src/test/resources/performance/medium.riddl", "~370 lines, 7 contexts"),
    TestFile("large", "testkit/jvm/src/test/resources/performance/large.riddl", "~1450 lines, 10 contexts")
  )

  /** Benchmark results for a single file */
  case class BenchmarkResult(
    name: String,
    sourceBytes: Int,
    bastBytes: Int,
    nodeCount: Int,
    stringTableSize: Int,
    coldParseMs: Double,
    coldLoadMs: Double,
    warmParseMs: Double,
    warmLoadMs: Double
  ) {
    def coldSpeedup: Double = coldParseMs / coldLoadMs
    def warmSpeedup: Double = warmParseMs / warmLoadMs
    def compressionRatio: Double = bastBytes.toDouble / sourceBytes
  }

  def main(args: Array[String]): Unit = {
    println("\n" + "=" * 80)
    println("BAST Performance Benchmark: Comprehensive Test Suite")
    println("=" * 80)
    println("\nThis benchmark compares RIDDL text parsing vs BAST binary loading")
    println("across multiple file sizes to measure performance scaling.\n")

    val results = scala.collection.mutable.ListBuffer.empty[BenchmarkResult]

    // Run benchmarks for each test file
    for testFile <- testFiles do
      if Files.exists(Paths.get(testFile.path)) then
        benchmarkFile(testFile) match {
          case Some(result) => results += result
          case None => println(s"  SKIPPED: ${testFile.name} (benchmark failed)")
        }
      else
        println(s"  SKIPPED: ${testFile.name} (file not found: ${testFile.path})")
      end if
    end for

    // Print summary table
    if results.nonEmpty then
      printSummaryTable(results.toSeq)
    end if

    println("\n" + "=" * 80)
    println("Benchmark Complete")
    println("=" * 80 + "\n")
  }

  private def benchmarkFile(testFile: TestFile): Option[BenchmarkResult] = {
    println("-" * 80)
    println(s"Benchmarking: ${testFile.name} (${testFile.description})")
    println("-" * 80)

    val url = URL.fromCwdPath(testFile.path)
    val inputFuture = RiddlParserInput.fromURL(url, testFile.name)

    try {
      Await.result(inputFuture.map { input =>
        println(s"  Source size: ${input.data.length} bytes")

        // Cold parse (first run)
        val coldParseStart = System.nanoTime()
        val parseResult = TopLevelParser.parseInput(input, false)
        val coldParseMs = (System.nanoTime() - coldParseStart) / 1_000_000.0

        parseResult match {
          case Right(root: Root) =>
            // Serialize to BAST
            val passInput = PassInput(root)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            val bastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
            val bastBytes = bastOutput.bytes

            println(f"  BAST size: ${bastBytes.length}%,d bytes (${100.0 * bastBytes.length / input.data.length}%.1f%% of source)")
            println(f"  Nodes: ${bastOutput.nodeCount}%,d, Strings: ${bastOutput.stringTableSize}%,d")

            // Cold load (first run)
            val coldLoadStart = System.nanoTime()
            val loadResult = BASTReader.read(bastBytes)
            val coldLoadMs = (System.nanoTime() - coldLoadStart) / 1_000_000.0

            loadResult match {
              case Right(_: Nebula) =>
                // Warm benchmark (50 iterations)
                val (warmParseMs, warmLoadMs) = runWarmBenchmark(input, bastBytes)

                println(f"  Cold: parse=${coldParseMs}%.2fms, load=${coldLoadMs}%.2fms (${coldParseMs/coldLoadMs}%.1fx)")
                println(f"  Warm: parse=${warmParseMs}%.2fms, load=${warmLoadMs}%.2fms (${warmParseMs/warmLoadMs}%.1fx)")

                Some(BenchmarkResult(
                  name = testFile.name,
                  sourceBytes = input.data.length,
                  bastBytes = bastBytes.length,
                  nodeCount = bastOutput.nodeCount,
                  stringTableSize = bastOutput.stringTableSize,
                  coldParseMs = coldParseMs,
                  coldLoadMs = coldLoadMs,
                  warmParseMs = warmParseMs,
                  warmLoadMs = warmLoadMs
                ))

              case Left(errors) =>
                println(s"  ERROR: BAST load failed: ${errors.map(_.format).mkString("; ")}")
                None
            }

          case Left(messages) =>
            println(s"  ERROR: Parse failed: ${messages.format}")
            None
        }
      }, 120.seconds)
    } catch {
      case ex: Exception =>
        println(s"  ERROR: ${ex.getMessage}")
        None
    }
  }

  private def runWarmBenchmark(input: RiddlParserInput, bastBytes: Array[Byte]): (Double, Double) = {
    val iterations = 50
    val warmupIterations = 5

    // Warmup
    for _ <- 0 until warmupIterations do
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

    (avgParseMs, avgLoadMs)
  }

  private def printSummaryTable(results: Seq[BenchmarkResult]): Unit = {
    println("\n" + "=" * 80)
    println("SUMMARY: Performance Across File Sizes")
    println("=" * 80)

    // Header
    println()
    println(f"${"File"}%-8s | ${"Source"}%8s | ${"BAST"}%8s | ${"Nodes"}%6s | ${"Cold"}%6s | ${"Warm"}%6s |")
    println(f"${""}%-8s | ${"(bytes)"}%8s | ${"(bytes)"}%8s | ${""}%6s | ${"Speed"}%6s | ${"Speed"}%6s |")
    println("-" * 60)

    for r <- results do
      println(f"${r.name}%-8s | ${r.sourceBytes}%8d | ${r.bastBytes}%8d | ${r.nodeCount}%6d | ${r.coldSpeedup}%5.1fx | ${r.warmSpeedup}%5.1fx |")
    end for

    println("-" * 60)

    // Calculate averages
    val avgColdSpeedup = results.map(_.coldSpeedup).sum / results.size
    val avgWarmSpeedup = results.map(_.warmSpeedup).sum / results.size

    println(f"${"Average"}%-8s | ${""}%8s | ${""}%8s | ${""}%6s | ${avgColdSpeedup}%5.1fx | ${avgWarmSpeedup}%5.1fx |")
    println()

    // Assessment
    if avgWarmSpeedup >= 10.0 then
      println(s"EXCELLENT: Average ${avgWarmSpeedup.toInt}x speedup exceeds 10x target!")
    else if avgWarmSpeedup >= 5.0 then
      println(s"GOOD: Average ${avgWarmSpeedup.toInt}x speedup meets expectations")
    else if avgWarmSpeedup >= 2.0 then
      println(s"ACCEPTABLE: Average ${avgWarmSpeedup.toInt}x speedup is meaningful")
    else
      println(s"NEEDS IMPROVEMENT: ${avgWarmSpeedup.toInt}x speedup below expectations")
    end if

    // Detailed timing breakdown
    println("\n" + "-" * 60)
    println("Detailed Timing (warm, 50 iterations average):")
    println("-" * 60)
    for r <- results do
      println(f"  ${r.name}%-8s: parse=${r.warmParseMs}%6.2fms, load=${r.warmLoadMs}%6.2fms")
    end for
  }
}
