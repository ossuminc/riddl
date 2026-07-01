/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

/** The committed example models under `riddlLib/json-examples` must survive
  * `parseJson -> root2Json -> parseJson` with the same validation profile and a
  * stable serialization. JVM-only (reads the example files from the build root).
  */
class Root2JsonExamplesTest extends AnyWordSpec with Matchers {

  private def read(rel: String): Option[String] =
    val f = new File(rel)
    if f.exists() then
      val s = Source.fromFile(f)
      try Some(s.mkString)
      finally s.close()
    else None

  private def profileOf(vr: RiddlLib.ValidateResult): Set[String] =
    (vr.errors ++ vr.warnings).map(_.format).toSet

  private def check(name: String): Unit =
    read(s"riddlLib/json-examples/$name") match
      case None => cancel(s"example $name not found relative to the build root")
      case Some(json) =>
        RiddlLib.parseJson(json, name) match
          case RiddlResult.Success(root) =>
            val before = profileOf(RiddlLib.validateRoot(root))
            val json2 = RiddlLib.root2Json(root)
            RiddlLib.parseJson(json2, name) match
              case RiddlResult.Success(root2) =>
                withClue(s"$name serialized:\n$json2\n") {
                  profileOf(RiddlLib.validateRoot(root2)) mustBe before
                  RiddlLib.root2Json(root2) mustBe json2
                }
              case RiddlResult.Failure(errors) =>
                fail(s"$name root2Json did not re-parse:\n$json2\n" + errors.map(_.format).mkString("\n"))
            end match
          case RiddlResult.Failure(errors) =>
            fail(s"$name parseJson failed: " + errors.map(_.format).mkString("\n"))
        end match

  "root2Json on the committed json-examples" should {
    "round-trip commerce.json" in { check("commerce.json") }
    "round-trip catalog.json" in { check("catalog.json") }
    "round-trip accounts.json" in { check("accounts.json") }
  }
}
