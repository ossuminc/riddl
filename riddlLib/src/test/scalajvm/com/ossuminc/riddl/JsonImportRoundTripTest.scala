/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

/** S61-2: the JSON surface of a model that contains a BAST load directive.
  *
  * JSON is a FLATTENED projection of the AST: `JsonifierPass` is a `HierarchyPass`, and its
  * traversal descends into a `BASTImport` without pushing the wrapper onto the parent stack. So the
  * loaded definitions are emitted as members of the container that holds the directive, and the AST
  * -> JSON -> AST -> JSON fixed point still holds. JVM-only because it needs a `.bast` file on
  * disk.
  */
class JsonImportRoundTripTest extends AnyWordSpec with Matchers {

  private val librarySource =
    """domain Lib is {
      |  context Accounts is {
      |    type Ledger is String
      |  }
      |}
      |""".stripMargin

  "A model containing a BAST load directive" should {

    "project the loaded definitions into the JSON and hold the JSON fixed point" in {
      val libRoot = RiddlLib.parseString(librarySource) match
        case RiddlResult.Success(r)      => r
        case RiddlResult.Failure(errors) => fail(s"library parse failed: $errors")
      val bytes = RiddlLib.ast2bast(libRoot) match
        case RiddlResult.Success(b)      => b
        case RiddlResult.Failure(errors) => fail(s"bast write failed: $errors")

      val dir = Files.createTempDirectory("json-load-round-trip")
      val bast = dir.resolve("lib.bast")
      Files.write(bast, bytes)
      try
        val model =
          s"""${"im" + "port"} "${bast.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin
        RiddlLib.parseString(model) match
          case RiddlResult.Failure(errors) => fail(s"model parse failed: $errors")
          case RiddlResult.Success(root0) =>
            val json1 = RiddlLib.root2Json(root0)
            // The loaded domain is present, not lost with the wrapper.
            json1 must include("\"Lib\"")
            json1 must include("\"App\"")
            RiddlLib.parseJson(json1) match
              case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
              case RiddlResult.Success(root1)  => RiddlLib.root2Json(root1) mustBe json1
            end match
        end match
      finally
        Files.deleteIfExists(bast)
        Files.deleteIfExists(dir)
      end try
    }
  }
}
