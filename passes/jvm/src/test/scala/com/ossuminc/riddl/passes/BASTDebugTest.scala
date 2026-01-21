/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Nebula, Root, Domain, Identifier, Contents}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.bast.{BASTReader, BASTWriter, ByteBufferWriter, StringTable, NODE_NEBULA, NODE_DOMAIN, NODE_IDENTIFIER, HEADER_SIZE}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class BASTDebugTest extends AnyWordSpec with Matchers {

  "BAST Debug" should {
    "trace byte-by-byte what is written and read" in {
      // Parse a simple domain
      val riddlText = """domain Simple is { ??? }"""
      val rpi = RiddlParserInput(riddlText, "test")
      val parseResult = TopLevelParser.parseInput(rpi, false)

      parseResult match {
        case Right(root: Root) =>
          println(s"Parsed root with ${root.contents.toSeq.size} items")

          // Serialize using BASTWriterPass
          val passInput = PassInput(root)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val bastOutput = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          val bytes = bastOutput.bytes

          println(s"\n=== BAST Output ===")
          println(s"Total bytes: ${bytes.length}")
          println(s"Node count: ${bastOutput.nodeCount}")
          println(s"String table size: ${bastOutput.stringTableSize}")

          // Dump first 100 bytes as hex
          println("\n=== First 100 bytes (hex) ===")
          bytes.take(100).grouped(16).zipWithIndex.foreach { case (group, i) =>
            val hex = group.map(b => f"${b & 0xFF}%02X").mkString(" ")
            val ascii = group.map(b => if b >= 32 && b < 127 then b.toChar else '.').mkString
            println(f"${i * 16}%04X: $hex%-48s | $ascii")
          }

          // Dump bytes around the string table
          val header = bytes.slice(0, HEADER_SIZE)
          val stringTableOffset = ((header(12) & 0xFF) << 24) | ((header(13) & 0xFF) << 16) |
                                  ((header(14) & 0xFF) << 8) | (header(15) & 0xFF)
          println(s"\nString table offset: $stringTableOffset")

          // Try to read back
          println("\n=== Attempting to read ===")
          val readResult = BASTReader.read(bytes)
          readResult match {
            case Right(nebula) =>
              println(s"Success! Nebula has ${nebula.contents.toSeq.size} items")
            case Left(errors) =>
              println(s"Read failed: ${errors.map(_.format).mkString("\n")}")
          }

        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }

    "analyze bytes around boundary for everything.riddl" in {
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "debug-test")

      val result = Await.result(inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(originalRoot: Root) =>
            val passInput = PassInput(originalRoot)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
            val bytes = output.bytes

            // Get string table offset from header (big-endian at offset 12-15)
            val stringTableOffset = ((bytes(12) & 0xFF) << 24) |
                                    ((bytes(13) & 0xFF) << 16) |
                                    ((bytes(14) & 0xFF) << 8) |
                                    (bytes(15) & 0xFF)

            println(s"\n=== Debug: everything.riddl ===")
            println(f"Total bytes: ${bytes.length}%,d")
            println(f"String table offset: $stringTableOffset (node data boundary)")
            println(f"Nodes: ${output.nodeCount}")

            // Print last 40 bytes before string table
            println(s"\n=== Last 40 bytes of node data (before $stringTableOffset) ===")
            val start = math.max(32, stringTableOffset - 40)
            for i <- start until stringTableOffset do {
              val b = bytes(i) & 0xFF
              println(f"  $i%4d: $b%3d (0x${b}%02X)")
            }

            // Print first 20 bytes of string table
            println(s"\n=== First 20 bytes of string table (at $stringTableOffset) ===")
            for i <- stringTableOffset until math.min(bytes.length, stringTableOffset + 20) do {
              val b = bytes(i) & 0xFF
              println(f"  $i%4d: $b%3d (0x${b}%02X) ${if b >= 32 && b < 127 then s"'${b.toChar}'" else ""}")
            }

            true
          case Left(messages) =>
            println(s"Parse failed: ${messages.format}")
            false
        }
      }, 30.seconds)

      assert(result)
    }
  }
}
