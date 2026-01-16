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
          val stringTableOffset = ((header(10) & 0xFF) << 24) | ((header(11) & 0xFF) << 16) |
                                  ((header(12) & 0xFF) << 8) | (header(13) & 0xFF)
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
  }
}
