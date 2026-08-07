/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Module, Root}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, AbstractTestingBasisWithTestData, Await, URL}
import org.scalatest.TestData

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Performance benchmark comparing RIDDL parsing vs BAST loading
  *
  * This test measures the key comparison:
  *   - Parse time: text → AST (baseline)
  *   - BAST load time: binary → AST (optimized approach)
  *
  * The goal is for BAST loading to be significantly faster (10-50x) than parsing.
  */
class BASTPerformanceBenchmark extends AbstractTestingBasisWithTestData {

  "BAST Performance" should {

    "compare parse vs load times for comprehensive test file" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(
        inputFuture.map { input =>
          // Step 1: Parse RIDDL and measure time
          val parseStart = System.nanoTime()
          val parseResult = TopLevelParser.parseInput(input, false)
          val parseTime = (System.nanoTime() - parseStart) / 1_000_000.0

          parseResult match {
            case Right(root: Root) =>
              // Step 2: Serialize to BAST
              val passInput = PassInput(root)
              val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
              val bastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
              val bastBytes = bastOutput.bytes

              // Step 3: Load from BAST and measure time
              val loadStart = System.nanoTime()
              val loadResult = BASTReader.read(bastBytes)
              val loadTime = (System.nanoTime() - loadStart) / 1_000_000.0

              loadResult match {
                case Right(module: Module) =>
                  val speedup = parseTime / loadTime

                  println(s"\n=== Comprehensive Test File Performance ===")
                  println(f"Source file: ${input.origin}")
                  println(f"Nodes: ${bastOutput.nodeCount}%,d")
                  println(
                    f"BAST size: ${bastBytes.length}%,d bytes (${bastBytes.length / 1024}%,d KB)"
                  )
                  println(f"String table: ${bastOutput.stringTableSize}%,d strings")
                  println()
                  println(f"Parse time (text→AST):  $parseTime%8.2f ms")
                  println(f"Load time (binary→AST): $loadTime%8.2f ms")
                  println(f"Speedup:                $speedup%8.1fx")
                  println()

                  // Verify we got a valid result
                  module.contents.toSeq must not be empty

                  // DELIBERATELY NOT GATED ON TIMING. This is one COLD, unwarmed parse against one
                  // cold load, so it measures JIT warmup as much as it measures either operation.
                  // Observed back-to-back on one machine: 0.9956x (a RED), then 13.0x, 9.3x, 6.1x,
                  // with parse alone ranging 2.03ms to 33.03ms within a single run. The effect is
                  // real and large; this measurement simply has more variance than headroom, so
                  // gating it here only teaches people to re-run reds. The warmed, 50-iteration
                  // benchmark below is what asserts the speedup.
                  info(f"Cold single-shot speedup: $speedup%.1fx (reported, not asserted)")

                  true

                case Left(errors) =>
                  println(s"BAST load failed: ${errors.map(_.format).mkString("\n")}")
                  false
              }

            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        },
        30.seconds
      )

      assert(result)
    }

    "benchmark multiple iterations for accurate timing" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(
        inputFuture.map { input =>
          val iterations = 50

          // Parse once to get the root and BAST bytes
          val parseResult = TopLevelParser.parseInput(input, false)
          parseResult match {
            case Right(root: Root) =>
              // Generate BAST once
              val passInput = PassInput(root)
              val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
              val bastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
              val bastBytes = bastOutput.bytes

              // Warmup for parse
              for _ <- 0 until 5 do
                val rpi = RiddlParserInput(input.data, input.root)
                TopLevelParser.parseInput(rpi, false)
              end for
              // Warmup for load
              for _ <- 0 until 5 do BASTReader.read(bastBytes)
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
              val bestSpeedup = maxParseMs / minLoadMs // Best case
              val worstSpeedup = minParseMs / maxLoadMs // Worst case

              println(s"\n=== Performance Benchmark ($iterations iterations) ===")
              println(f"Nodes: ${bastOutput.nodeCount}%,d")
              println(f"BAST size: ${bastBytes.length}%,d bytes")
              println()
              println("Parse (text→AST):")
              println(f"  Average: $avgParseMs%8.2f ms")
              println(f"  Min:     $minParseMs%8.2f ms")
              println(f"  Max:     $maxParseMs%8.2f ms")
              println()
              println("Load (binary→AST):")
              println(f"  Average: $avgLoadMs%8.2f ms")
              println(f"  Min:     $minLoadMs%8.2f ms")
              println(f"  Max:     $maxLoadMs%8.2f ms")
              println()
              // Gate on the MEDIAN, not the mean. A single scheduling hiccup (parse was observed
              // spiking 2ms -> 33ms mid-run) drags the mean but not the median, and this assertion
              // exists to catch a real regression -- BAST loading ceasing to beat parsing -- not to
              // report machine load.
              def medianMs(times: Array[Long]): Double =
                val sorted = times.sorted
                val n = sorted.length
                val mid =
                  if n % 2 == 1 then sorted(n / 2).toDouble
                  else (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
                mid / 1_000_000.0
              end medianMs

              val medianSpeedup = medianMs(parseTimes) / medianMs(loadTimes)

              println(f"Average speedup: $avgSpeedup%6.1fx")
              println(f"Median speedup:  $medianSpeedup%6.1fx")
              println(f"Best case:       $bestSpeedup%6.1fx")
              println(f"Worst case:      $worstSpeedup%6.1fx")

              // The real warmed ratio runs 6x-13x, so 1.0 is a floor with an order of magnitude of
              // headroom: it fails only if the speedup genuinely disappears.
              medianSpeedup must be > 1.0

              true

            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        },
        120.seconds
      )

      assert(result)
    }

    "measure throughput for write and read operations" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(
        inputFuture.map { input =>
          val parseResult = TopLevelParser.parseInput(input, false)
          parseResult match {
            case Right(root: Root) =>
              val iterations = 100

              // Warmup
              for _ <- 0 until 10 do
                val passInput = PassInput(root)
                val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
                val bastBytes = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get.bytes
                BASTReader.read(bastBytes)
              end for
              // Measure write
              val writeTimes = new Array[Long](iterations)
              var lastBastBytes: Array[Byte] = Array.empty
              var lastOutput: BASTOutput = null

              for i <- 0 until iterations do
                val start = System.nanoTime()
                val passInput = PassInput(root)
                val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
                lastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
                lastBastBytes = lastOutput.bytes
                writeTimes(i) = System.nanoTime() - start
              end for
              // Measure read
              val readTimes = new Array[Long](iterations)
              for i <- 0 until iterations do
                val start = System.nanoTime()
                BASTReader.read(lastBastBytes)
                readTimes(i) = System.nanoTime() - start
              end for

              val avgWriteMs = writeTimes.sum / iterations / 1_000_000.0
              val avgReadMs = readTimes.sum / iterations / 1_000_000.0

              val writeBytesPerSec = (lastBastBytes.length / avgWriteMs * 1000.0).toLong
              val readBytesPerSec = (lastBastBytes.length / avgReadMs * 1000.0).toLong
              val writeNodesPerSec = (lastOutput.nodeCount / avgWriteMs * 1000.0).toLong
              val readNodesPerSec = (lastOutput.nodeCount / avgReadMs * 1000.0).toLong

              println(s"\n=== Throughput Benchmark ($iterations iterations) ===")
              println(f"BAST size: ${lastBastBytes.length}%,d bytes")
              println(f"Nodes: ${lastOutput.nodeCount}%,d")
              println()
              println("Write (AST→binary):")
              println(f"  Average time: $avgWriteMs%8.2f ms")
              println(f"  Throughput:   ${writeBytesPerSec / 1024}%,8d KB/s")
              println(f"  Node rate:    $writeNodesPerSec%,8d nodes/s")
              println()
              println("Read (binary→AST):")
              println(f"  Average time: $avgReadMs%8.2f ms")
              println(f"  Throughput:   ${readBytesPerSec / 1024}%,8d KB/s")
              println(f"  Node rate:    $readNodesPerSec%,8d nodes/s")
              println()
              println(f"Read/Write speed ratio: ${avgWriteMs / avgReadMs}%.1fx")

              true

            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        },
        120.seconds
      )

      assert(result)
    }
  }
}
