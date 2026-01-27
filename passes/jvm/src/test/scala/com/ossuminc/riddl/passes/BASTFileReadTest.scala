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

/** Test BAST serialization and deserialization */
class BASTFileReadTest extends AnyWordSpec {

  "BAST File Read" should {
    "serialize and deserialize everything.riddl" in {
      // Step 1: Generate bytes in memory from everything.riddl
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

      println(s"BAST serialization complete: ${inMemoryBytes.length} bytes")

      // Step 2: Deserialize the bytes back to AST
      println("\n--- Deserializing in-memory bytes ---")
      val result = BASTReader.read(inMemoryBytes)
      result match {
        case Right(nebula) =>
          println(s"Success! Nebula has ${nebula.contents.toSeq.size} items")
        case Left(errors) =>
          println(s"FAILED - ${errors.format}")
      }

      assert(result.isRight, s"Deserialization failed: ${result.swap.getOrElse(Nil).toString}")
      succeed
    }
  }
}
