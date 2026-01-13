/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}

import scala.concurrent.duration.*

/** Performance benchmark comparing RIDDL parse time vs BAST read time
  *
  * Run with: sbt "bast/Test/runMain com.ossuminc.riddl.bast.BenchmarkRunner"
  */
object BenchmarkRunner {

  def main(args: Array[String]): Unit = {
    println("\n" + "=" * 80)
    println("BAST Performance Benchmark")
    println("=" * 80)

    benchmarkToDoodles()
    benchmarkShopifyCart()

    println("\n" + "=" * 80)
    println("Benchmark Complete")
    println("=" * 80 + "\n")
  }

  private def runBenchmark(input: RiddlParserInput, parseTrials: Int, bastTrials: Int): Unit = {
    // Warmup: Parse once to JIT compile
    TopLevelParser.parseInput(input, true)

    // Benchmark: Parse time
    val parseStart = System.nanoTime()
    var parseSuccessCount = 0
    for (i <- 1 to parseTrials) {
      TopLevelParser.parseInput(input, true) match {
        case Right(_) => parseSuccessCount += 1
        case Left(_) =>
      }
    }
    val parseEnd = System.nanoTime()
    val parseTimeMs = (parseEnd - parseStart) / 1_000_000.0
    val avgParseTimeMs = parseTimeMs / parseTrials

    // Generate BAST once
    val parseResult = TopLevelParser.parseInput(input, true)
    parseResult match {
      case Right(root) =>
        val passInput = PassInput(root)
        val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
        val output = writerResult.outputOf[BASTOutput](BASTWriter.name).get
        val bastBytes = output.bytes

        // Warmup: Read BAST once
        BASTReader.read(bastBytes)

        // Benchmark: BAST read time
        val bastStart = System.nanoTime()
        var bastSuccessCount = 0
        var bastErrors: Option[Messages.Messages] = None
        for (i <- 1 to bastTrials) {
          BASTReader.read(bastBytes) match {
            case Right(_) => bastSuccessCount += 1
            case Left(errors) =>
              if bastErrors.isEmpty then bastErrors = Some(errors)
          }
        }
        val bastEnd = System.nanoTime()
        val bastTimeMs = (bastEnd - bastStart) / 1_000_000.0
        val avgBastTimeMs = bastTimeMs / bastTrials

        // Calculate speedup
        val speedup = avgParseTimeMs / avgBastTimeMs

        // Print results
        println(f"\nResults (average of $parseTrials trials):")
        println(f"  Parse time:      ${avgParseTimeMs}%.2f ms")
        println(f"  BAST read time:  ${avgBastTimeMs}%.2f ms")
        println(f"  Speedup:         ${speedup}%.1fx faster")
        println(f"  BAST size:       ${bastBytes.length}%,d bytes")
        println(f"  Success rate:    $parseSuccessCount/$parseTrials parse, $bastSuccessCount/$bastTrials BAST")

        // Print BAST read errors if any
        bastErrors.foreach { errors =>
          println(f"\n⚠ BAST read errors:")
          errors.toSeq.take(5).foreach { msg =>
            println(s"  - ${msg.format}")
          }
        }

        // Categorize result
        if speedup >= 10.0 then
          println(f"\n✓ EXCELLENT: BAST is ${speedup}%.1fx faster than parsing")
        else if speedup >= 5.0 then
          println(f"\n✓ GOOD: BAST is ${speedup}%.1fx faster than parsing")
        else if speedup >= 2.0 then
          println(f"\n✓ MODERATE: BAST is ${speedup}%.1fx faster than parsing")
        else if speedup >= 1.0 then
          println(f"\n⚠ MARGINAL: BAST is only ${speedup}%.1fx faster than parsing")
        else
          println(f"\n✗ SLOWER: BAST is ${1.0/speedup}%.1fx slower than parsing")
        end if

      case Left(errors) =>
        println(s"\n✗ Parse failed with ${errors.size} errors:")
        errors.toSeq.take(5).foreach { msg =>
          println(s"  - ${msg.format}")
        }
    }
  }

  private def benchmarkToDoodles(): Unit = {
    println("\n### Benchmarking: ToDoodles Project ###")

    val url = URL.fromCwdPath("../riddl-examples/src/riddl/ToDoodles/ToDoodles.riddl")
    val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

    Await.result(inputFuture.map { input =>
      runBenchmark(input, parseTrials = 10, bastTrials = 10)
    }, 30.seconds)
  }

  private def benchmarkShopifyCart(): Unit = {
    println("\n### Benchmarking: ShopifyCart Project (1,543 lines) ###")

    val url = URL.fromCwdPath("../riddl-examples/src/riddl/ShopifyCart/shopify-cart.riddl")
    val inputFuture = RiddlParserInput.fromURL(url, "benchmark")

    Await.result(inputFuture.map { input =>
      runBenchmark(input, parseTrials = 10, bastTrials = 10)
    }, 30.seconds)
  }
}
