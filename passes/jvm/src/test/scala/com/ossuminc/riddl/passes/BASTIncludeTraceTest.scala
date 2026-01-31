/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

/** Debug test for tracing Include serialization structure */
class BASTIncludeTraceTest extends AnyWordSpec {

  "BAST Include Trace" should {

    "trace include structure in everything.riddl" in {
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "include-trace-test")

      val result = Await.result(inputFuture.map { input =>
        val parseResult = TopLevelParser.parseInput(input, true)
        parseResult match {
          case Right(originalRoot: Root) =>
            println(s"\n=== Include Trace: everything.riddl ===")

            // Find all includes in the parsed AST
            def findIncludes(value: RiddlValue, depth: Int = 0): Unit = {
              val indent = "  " * depth
              value match {
                case i: Include[?] =>
                  println(s"${indent}Include at ${i.loc}: origin=${i.origin}, contents count=${i.contents.length}")
                  i.contents.toSeq.foreach(c => findIncludes(c, depth + 1))
                case d: Domain =>
                  println(s"${indent}Domain ${d.id.value} at ${d.loc}")
                  d.contents.toSeq.foreach(c => findIncludes(c, depth + 1))
                case r: Root =>
                  println(s"${indent}Root with ${r.contents.length} items")
                  r.contents.toSeq.foreach(c => findIncludes(c, depth + 1))
                case c: Context =>
                  println(s"${indent}Context ${c.id.value} at ${c.loc}")
                  c.contents.toSeq.foreach(v => findIncludes(v, depth + 1))
                case e: Entity =>
                  println(s"${indent}Entity ${e.id.value} at ${e.loc}")
                case _ =>
                  // Skip other node types for brevity
              }
            }

            findIncludes(originalRoot)

            // Now serialize and analyze bytes
            val passInput = PassInput(originalRoot)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
            val bytes = output.bytes

            // Get string table offset (big-endian)
            val stringTableOffset = ((bytes(12) & 0xFF) << 24) |
                                    ((bytes(13) & 0xFF) << 16) |
                                    ((bytes(14) & 0xFF) << 8) |
                                    (bytes(15) & 0xFF)

            println(f"\n=== BAST Output ===")
            println(f"Total bytes: ${bytes.length}%,d")
            println(f"String table offset: $stringTableOffset (node data boundary)")

            // Find all NODE_INCLUDE tags in the bytes
            println(s"\n=== NODE_INCLUDE (30) occurrences ===")
            for pos <- HEADER_SIZE until stringTableOffset do
              val b = bytes(pos) & 0xFF
              if b == NODE_INCLUDE || b == (NODE_INCLUDE | 0x80) then
                // Also show surrounding context
                val start = math.max(HEADER_SIZE, pos - 10)
                val end = math.min(stringTableOffset, pos + 20)
                val context = bytes.slice(start, end).map(x => f"${x & 0xFF}%02X").mkString(" ")
                println(f"  Position $pos%4d: tagByte=$b%3d (with metadata flag: ${(b & 0x80) != 0})")
                println(f"    Context [$start-$end]: $context")
              end if
            end for

            // Find FILE_CHANGE_MARKER patterns
            println(s"\n=== FILE_CHANGE_MARKER (0) occurrences ===")
            for pos <- HEADER_SIZE until stringTableOffset do
              val b = bytes(pos) & 0xFF
              if b == FILE_CHANGE_MARKER && pos > HEADER_SIZE then
                // Read string table index that follows
                val stringIdx = bytes(pos + 1) & 0xFF
                val tagAfter = if pos + 2 < stringTableOffset then bytes(pos + 2) & 0xFF else -1
                println(f"  Position $pos%4d: FILE_CHANGE_MARKER, stringIdx=$stringIdx, next tag=$tagAfter")
              end if
            end for

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
