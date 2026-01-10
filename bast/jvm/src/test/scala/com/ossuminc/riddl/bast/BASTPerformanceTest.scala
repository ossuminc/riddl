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
import org.scalatest.TestData
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

class BASTPerformanceTest extends AnyWordSpec {

  "BAST Performance" should {
    "measure comprehensive test file" in { (td: TestData) =>
      val url = URL.fromCwdPath("bast/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(root: Root) =>
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriter.creator()))
            val output = result.outputOf[BASTOutput](BASTWriter.name).get

            println(s"\n=== Comprehensive Test File ===")
            println(f"Nodes: ${output.nodeCount}%,d")
            println(f"Size: ${output.bytes.length}%,d bytes (${output.bytes.length / 1024}%,d KB)")
            println(f"String table: ${output.stringTableSize}%,d strings")

            true
          case Left(messages) =>
            println(s"Parse failed: ${messages.format}")
            false
        }
      }, 30.seconds)

      assert(result)
    }

    "measure ToDoodles project" in { (td: TestData) =>
      val examplesPath = Paths.get("/Users/reid/Code/ossuminc/riddl-examples")
      if !Files.exists(examplesPath) then
        println("riddl-examples not found, skipping test")
        succeed
      else
        val url = URL.fromCwdPath("../riddl-examples/src/riddl/ToDoodles/ToDoodles.riddl")
        val inputFuture = RiddlParserInput.fromURL(url, td.name)

        val result = Await.result(inputFuture.map { input =>
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

              println(s"\n=== ToDoodles Project ===")
              println(f"Parse time: $parseTime%.2f ms")
              println(f"BAST write time: $bastTime%.2f ms")
              println(f"Nodes: ${output.nodeCount}%,d")
              println(f"Size: ${output.bytes.length}%,d bytes (${output.bytes.length / 1024}%,d KB)")
              println(f"String table: ${output.stringTableSize}%,d strings")
              println(f"Write/Parse ratio: ${bastTime / parseTime}%.2fx")

              true
            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        }, 30.seconds)

        assert(result)
    }

    "measure ReactiveBBQ domain" in { (td: TestData) =>
      val examplesPath = Paths.get("/Users/reid/Code/ossuminc/riddl-examples")
      if !Files.exists(examplesPath) then
        println("riddl-examples not found, skipping test")
        succeed
      else
        val url = URL.fromCwdPath("../riddl-examples/src/riddl/ReactiveBBQ/restaurant/domain.riddl")
        val inputFuture = RiddlParserInput.fromURL(url, td.name)

        val result = Await.result(inputFuture.map { input =>
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

              println(s"\n=== ReactiveBBQ Restaurant Domain ===")
              println(f"Parse time: $parseTime%.2f ms")
              println(f"BAST write time: $bastTime%.2f ms")
              println(f"Nodes: ${output.nodeCount}%,d")
              println(f"Size: ${output.bytes.length}%,d bytes (${output.bytes.length / 1024}%,d KB)")
              println(f"String table: ${output.stringTableSize}%,d strings")
              println(f"Write/Parse ratio: ${bastTime / parseTime}%.2fx")

              true
            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        }, 30.seconds)

        assert(result)
    }

    "measure serialization throughput" in { (td: TestData) =>
      val url = URL.fromCwdPath("bast/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td.name)

      val result = Await.result(inputFuture.map { input =>
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

            println(s"\n=== Performance Benchmark (100 iterations) ===")
            println(f"Average time: $avgMillis%.2f ms")
            println(f"Min time: $minMillis%.2f ms")
            println(f"Max time: $maxMillis%.2f ms")
            println(f"Throughput: ${bytesPerSec / 1024}%,d KB/s")
            println(f"Node rate: $nodesPerSec%,d nodes/s")

            true
          case Left(messages) =>
            println(s"Parse failed: ${messages.format}")
            false
        }
      }, 60.seconds)

      assert(result)
    }
  }
}
