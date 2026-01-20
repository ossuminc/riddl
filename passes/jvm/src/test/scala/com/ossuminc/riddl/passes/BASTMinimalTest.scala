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
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/** Minimal test to debug BAST deserialization */
class BASTMinimalTest extends AnyWordSpec with Matchers {

  "BAST Minimal" should {

    "serialize and deserialize simple domain inline" in {
      val riddlText = """domain Simple is { type Foo is String }"""
      val input = RiddlParserInput(riddlText, "minimal-test")

      println(s"\n=== Minimal BAST Test ===")
      println(s"Input size: ${input.data.length} chars")

      // Parse
      val parseResult = TopLevelParser.parseInput(input, true)
      parseResult match {
        case Right(root: Root) =>
          println(s"Parsed: ${root.contents.toSeq.size} top-level items")

          // Serialize
          val passInput = PassInput(root)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          val bastBytes = output.bytes

          println(s"Serialized: ${bastBytes.length} bytes, ${output.nodeCount} nodes")

          // Read
          println(s"\n--- Attempting to read ---")
          BASTReader.read(bastBytes) match {
            case Right(nebula) =>
              println(s"SUCCESS: Nebula with ${nebula.contents.toSeq.size} items")
              succeed
            case Left(errors) =>
              fail(s"BAST read failed: ${errors.format}")
          }

        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }
  }
}
