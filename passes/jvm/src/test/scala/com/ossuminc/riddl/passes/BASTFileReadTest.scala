/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Test reading BAST from file */
class BASTFileReadTest extends AnyWordSpec {

  "BAST File Read" should {
    "compare in-memory bytes vs file bytes" in {
      // Step 1: Generate bytes in memory
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "test")

      val inMemoryBytes = Await.result(inputFuture.map { input =>
        TopLevelParser.parseInput(input, true) match {
          case Right(originalRoot: Root) =>
            val passInput = PassInput(originalRoot)
            val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
            writerResult.outputOf[BASTOutput](BASTWriterPass.name).get.bytes
          case Left(msgs) =>
            fail(s"Parse failed: ${msgs.format}")
        }
      }, 30.seconds)

      // Step 2: Read bytes from file
      val fileBytes = Files.readAllBytes(Paths.get("language/input/everything.bast"))

      println(s"In-memory bytes: ${inMemoryBytes.length}")
      println(s"File bytes: ${fileBytes.length}")

      // Step 3: Compare
      if inMemoryBytes.length != fileBytes.length then
        println("DIFFERENT LENGTHS!")
      else
        var firstDiff = -1
        for i <- 0 until inMemoryBytes.length do
          if inMemoryBytes(i) != fileBytes(i) && firstDiff == -1 then
            firstDiff = i
        end for
        if firstDiff >= 0 then
          println(s"First difference at byte $firstDiff")
          println(s"  In-memory: ${inMemoryBytes.slice(firstDiff, firstDiff + 20).map(b => f"${b & 0xFF}%02X").mkString(" ")}")
          println(s"  File:      ${fileBytes.slice(firstDiff, firstDiff + 20).map(b => f"${b & 0xFF}%02X").mkString(" ")}")
        else
          println("Bytes are IDENTICAL!")
        end if
      end if

      // Try deserializing with a fresh reader
      println("\n--- Deserializing in-memory bytes (fresh reader) ---")
      val result1 = BASTReader.read(inMemoryBytes)
      result1 match {
        case Right(nebula) => println(s"In-memory: Success! ${nebula.contents.toSeq.size} items")
        case Left(errors) => println(s"In-memory: FAILED - ${errors.format}")
      }

      assert(result1.isRight, s"Deserialization failed: ${result1.swap.getOrElse(Nil).toString}")
      succeed
    }
  }
}
