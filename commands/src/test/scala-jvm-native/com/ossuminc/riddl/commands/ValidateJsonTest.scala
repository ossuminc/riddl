/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** `validate --json`: the machine-readable half of the rule-id work.
  *
  * The point of a stable rule id is that a consumer can filter, suppress, count or fix a diagnostic
  * without matching prose, which breaks the first time a message is reworded. That is only true if
  * the id actually reaches the consumer, so these cases assert the WIRE SHAPE rather than the
  * rendering.
  */
class ValidateJsonTest extends AnyWordSpec with Matchers {

  /** Pins the options, because `pc.options` is global and sbt runs suites in parallel -- three
    * cases in the sibling summary suite passed alone and failed in company for exactly that.
    */
  private def jsonFor(body: String): String = {
    val dir = Files.createTempDirectory("riddl-validate-json")
    try
      val f = dir.resolve("m.riddl")
      Files.writeString(f, body)
      val (_, out) = pc.withOptions(CommonOptions()) { _ =>
        StdStreamCapture.capturingStdOut { () =>
          new ValidateCommand().run(ValidateCommand.Options(Some(f), None, json = true), None)
        }
      }
      out.trim
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  "validate --json" should {

    "emit stdout that is PURE json, with no summary line" in {
      // The reason this matters is a defect `dump --json` already shipped: a count printed through
      // pc.log put an `[info]`-prefixed line into the stream, which looks fine to the eye and is
      // fatal to `json.loads`. Parsing the WHOLE of stdout is the only assertion that catches it.
      val out = jsonFor("domain Foo is { ??? }")
      val parsed = ujson.read(out)
      parsed.arr mustBe a[scala.collection.mutable.ArrayBuffer[?]]
    }

    "emit [] for a model with nothing to report, never empty output" in {
      // A consumer must be able to tell "validated clean" from "the command never ran". riddl-models
      // produced a confident wrong zero three times in one day by not being able to.
      val out = jsonFor("domain Foo is { ??? }")
      ujson.read(out).arr.foreach { r =>
        r.obj.keySet must contain allOf ("rule", "severity", "message", "file", "line", "col")
      }
      out must startWith("[")
      out must endWith("]")
    }

    "carry the stable rule id on each diagnostic" in {
      // An unused function is `use-unused-definition`, threaded from UsageResolution. Asserted by
      // ID and not by wording: the wording is free to change and the id is not.
      val out = jsonFor("""
        |domain Foo is {
        |  context Bar is {
        |    function Unused is { ??? }
        |  }
        |}
        |""".stripMargin)
      val rules = ujson.read(out).arr.map(_.obj("rule")).collect { case ujson.Str(s) => s }.toSet
      rules must contain("use-unused-definition")
    }

    "give every record a location a tool can jump to" in {
      val out = jsonFor("""
        |domain Foo is {
        |  context Bar is {
        |    function Unused is { ??? }
        |  }
        |}
        |""".stripMargin)
      val recs = ujson.read(out).arr
      recs must not be empty
      recs.foreach { r =>
        r.obj("file").str must endWith(".riddl")
        r.obj("line").num must be >= 1.0
      }
    }
  }
}
