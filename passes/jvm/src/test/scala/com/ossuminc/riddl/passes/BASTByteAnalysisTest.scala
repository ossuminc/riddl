/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.bast.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

/** Debug test for analyzing BAST bytes around misalignment position */
class BASTByteAnalysisTest extends AnyWordSpec {

  "BAST Byte Analysis" should {

    "analyze bytes around position 1443 for everything.riddl" in {
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "byte-analysis-test")

      val result = Await.result(inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(originalRoot: Root) =>
            println(s"\n=== BAST Byte Analysis: everything.riddl ===")

            // Serialize AST -> BAST binary
            val passInput = PassInput(originalRoot)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
            val bytes = output.bytes

            println(f"BAST written: ${bytes.length}%,d bytes (${output.nodeCount}%,d nodes)")

            // Get string table offset from header (big-endian, bytes 12-15)
            val stringTableOffset = ((bytes(12) & 0xFF) << 24) |
                                    ((bytes(13) & 0xFF) << 16) |
                                    ((bytes(14) & 0xFF) << 8) |
                                    (bytes(15) & 0xFF)

            println(f"String table offset: $stringTableOffset")

            // Dump bytes from 1400 to 1500 (around the problematic position)
            println(s"\n=== Bytes from 1400 to 1500 ===")
            val start = 1400
            val end = math.min(1500, stringTableOffset)

            bytes.slice(start, end).zipWithIndex.grouped(16).foreach { group =>
              val offset = start + group.head._2
              val hexBytes = group.map { case (b, _) => f"${b & 0xFF}%02X" }.mkString(" ")
              val asciiChars = group.map { case (b, _) =>
                val c = b & 0xFF
                if c >= 32 && c < 127 then c.toChar else '.'
              }.mkString
              println(f"$offset%04d: $hexBytes%-48s | $asciiChars")
            }

            // Print specific byte values around 1443-1450
            println(s"\n=== Detailed analysis around 1443-1450 ===")
            for i <- 1440 until math.min(1460, stringTableOffset) do {
              val b = bytes(i) & 0xFF
              val tagName = b match {
                case NODE_NEBULA => "NODE_NEBULA"
                case NODE_DOMAIN => "NODE_DOMAIN"
                case NODE_CONTEXT => "NODE_CONTEXT"
                case NODE_ENTITY => "NODE_ENTITY"
                case NODE_INCLUDE => "NODE_INCLUDE"
                case NODE_OUTPUT => "NODE_OUTPUT"
                case NODE_INPUT => "NODE_INPUT"
                case FILE_CHANGE_MARKER => "FILE_CHANGE_MARKER"
                case _ if b >= 32 && b < 127 => s"ascii '${b.toChar}'"
                case _ => s"value"
              }
              println(f"  $i%4d: $b%3d (0x$b%02X) - $tagName")
            }

            // Also dump bytes around position 914-930 (Repository issue)
            println(s"\n=== Detailed analysis around 914-930 ===")
            for i <- 910 until math.min(940, stringTableOffset) do {
              val b = bytes(i) & 0xFF
              val tagName = b match {
                case NODE_PROJECTOR => "NODE_PROJECTOR"
                case NODE_REPOSITORY => "NODE_REPOSITORY"
                case NODE_CONTEXT => "NODE_CONTEXT"
                case NODE_TYPE => "NODE_TYPE"
                case NODE_HANDLER => "NODE_HANDLER"
                case NODE_SCHEMA => "NODE_SCHEMA"
                case _ if (b & 0x7F) == NODE_PROJECTOR => s"NODE_PROJECTOR|METADATA"
                case _ if (b & 0x7F) == NODE_REPOSITORY => s"NODE_REPOSITORY|METADATA"
                case FILE_CHANGE_MARKER => "FILE_CHANGE_MARKER"
                case _ if b >= 32 && b < 127 => s"ascii '${b.toChar}'"
                case _ => s"value"
              }
              println(f"  $i%4d: $b%3d (0x$b%02X) - $tagName")
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
