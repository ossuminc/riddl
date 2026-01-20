/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Root, Nebula}
import com.ossuminc.riddl.language.bast.{ByteBufferReader, MAGIC_BYTES, VERSION, HEADER_SIZE, Flags}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult, BASTWriterPass, BASTOutput}
import com.ossuminc.riddl.passes.AbstractRunPassTest
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.TestData

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

class BASTWriterSpec extends AbstractRunPassTest {

  "BASTWriter" should {
    "serialize comprehensive test file" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td)

      inputFuture.map { input =>
        // Parse the input
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Left(messages) =>
            fail(s"Parse failed:\n${messages.format}")

          case Right(root: Root) =>
            // Verify we have a valid AST
            root.contents.toSeq.size must be > 0

            // Run BASTWriter pass
            val passInput = PassInput(root)
            val bastWriter = BASTWriterPass.creator()
            val result = Pass.runThesePasses(passInput, Seq(bastWriter))

            if result.messages.hasErrors then
              fail(s"BASTWriter failed:\n${result.messages.format}")

            // Get the BAST output
            val bastOutput = result.outputOf[BASTOutput](BASTWriterPass.name)
            bastOutput must be (defined)

            val output = bastOutput.get
            val bytes = output.bytes

            // Validate BAST structure
            validateBASTStructure(bytes)

            info(s"Serialized ${output.nodeCount} nodes to ${bytes.length} bytes")
            info(s"String table: ${output.stringTableSize} strings")

            succeed
        }
      }

      Await.result(inputFuture, 30.seconds)
    }

    "validate BAST header structure" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td)

      inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(root: Root) =>
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))

            val bytes = result.outputOf[BASTOutput](BASTWriterPass.name).get.bytes

            // Validate header
            val reader = ByteBufferReader(bytes)

            // Magic bytes
            val magic = reader.readRawBytes(4)
            magic must equal (MAGIC_BYTES)

            // Version (single 32-bit integer)
            val version = reader.readInt()
            version must equal (VERSION)

            // Flags
            val flags = reader.readShort()
            (flags & Flags.WITH_LOCATIONS) must not equal (0)
            reader.readShort() // reserved

            // Offsets
            val stringTableOffset = reader.readInt()
            val rootOffset = reader.readInt()
            val fileSize = reader.readInt()

            stringTableOffset must be > HEADER_SIZE
            rootOffset must be > stringTableOffset
            fileSize must equal (bytes.length)

            info(s"Header validated: v$version, ${bytes.length} bytes")
            succeed

          case Left(messages) =>
            fail(s"Parse failed: ${messages.format}")
        }
      }

      Await.result(inputFuture, 30.seconds)
    }

    "serialize and validate string table" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td)

      inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(root: Root) =>
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))

            val bytes = result.outputOf[BASTOutput](BASTWriterPass.name).get.bytes
            val reader = ByteBufferReader(bytes)

            // Skip header
            reader.skip(HEADER_SIZE)

            // Read string table
            val stringCount = reader.readVarInt()
            stringCount must be > 0

            info(s"String table contains $stringCount strings")

            // Verify we can read all strings
            var readStrings = 0
            while readStrings < stringCount do
              val length = reader.readVarInt()
              val strBytes = reader.readRawBytes(length)
              val str = new String(strBytes, "UTF-8")
              str.nonEmpty must be (true)
              readStrings += 1
            end while

            readStrings must equal (stringCount)
            succeed

          case Left(messages) =>
            fail(s"Parse failed: ${messages.format}")
        }
      }

      Await.result(inputFuture, 30.seconds)
    }

    "measure serialization performance" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td)

      inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(root: Root) =>
            val iterations = 100
            val times = new Array[Long](iterations)

            // Warmup
            for _ <- 0 until 10 do
              val passInput = PassInput(root)
              Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            end for

            // Measure
            var lastResult: PassesResult = null
            for i <- 0 until iterations do
              val start = System.nanoTime()
              val passInput = PassInput(root)
              val result = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
              val end = System.nanoTime()
              times(i) = end - start
              lastResult = result

              if result.messages.hasErrors then
                fail(s"BASTWriter failed on iteration $i")
            end for

            val avgNanos = times.sum / iterations
            val avgMillis = avgNanos / 1_000_000.0
            val minMillis = times.min / 1_000_000.0
            val maxMillis = times.max / 1_000_000.0

            val output = lastResult.outputOf[BASTOutput](BASTWriterPass.name).get
            val bytesPerSec = (output.bytes.length / avgMillis * 1000.0).toLong
            val kbPerSec = bytesPerSec / 1024

            info(f"Performance over $iterations iterations:")
            info(f"  Average: $avgMillis%.2f ms")
            info(f"  Min: $minMillis%.2f ms")
            info(f"  Max: $maxMillis%.2f ms")
            info(s"  Throughput: $kbPerSec KB/s")
            info(f"  Nodes/sec: ${(output.nodeCount / avgMillis * 1000.0).toLong}")

            succeed

          case Left(messages) =>
            fail(s"Parse failed: ${messages.format}")
        }
      }

      Await.result(inputFuture, 60.seconds)
    }

    "serialize all node types without errors" in { (td: TestData) =>
      val url = URL.fromCwdPath("passes/jvm/src/test/resources/comprehensive-test.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, td)

      inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(root: Root) =>
            val passInput = PassInput(root)
            val result = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))

            // Check for any warnings about unhandled node types
            val warnings = result.messages.justWarnings
            val unhandledWarnings = warnings.filter(_.message.contains("Unhandled node type"))

            if unhandledWarnings.nonEmpty then
              fail(s"Found unhandled node types:\n${unhandledWarnings.format}")

            info(s"Successfully serialized all node types without warnings")
            succeed

          case Left(messages) =>
            fail(s"Parse failed: ${messages.format}")
        }
      }

      Await.result(inputFuture, 30.seconds)
    }
  }

  private def validateBASTStructure(bytes: Array[Byte]): Unit = {
    // Must have minimum size (header + some content)
    bytes.length must be >= HEADER_SIZE

    // Magic bytes must be correct
    val magic = bytes.slice(0, 4)
    magic must equal (MAGIC_BYTES)

    val reader = ByteBufferReader(bytes)

    // Parse header
    reader.skip(4) // magic
    val version = reader.readInt()
    val flags = reader.readShort()
    reader.readShort() // reserved
    val stringTableOffset = reader.readInt()
    val rootOffset = reader.readInt()
    val fileSize = reader.readInt()

    // Validate offsets
    stringTableOffset must be >= HEADER_SIZE
    stringTableOffset must be < bytes.length
    rootOffset must be > stringTableOffset
    rootOffset must be < bytes.length
    fileSize must equal (bytes.length)

    // Validate version
    version must equal (VERSION)
  }
}
