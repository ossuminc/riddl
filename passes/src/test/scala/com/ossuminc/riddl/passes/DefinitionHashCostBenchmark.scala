/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.toSeq
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}

import scala.collection.mutable

/** Measures the cost of hashing a `Definition`, which is what every `HashMap[Definition, _]` and
  * `mutable.Set[Definition]` in ResolutionPass and Usages pays on every operation.
  *
  * **Why this exists.** `TypeTestCostBenchmark` refuted the ClassTag hypothesis outright — Scala.js
  * runs `classTag.runtimeClass.isAssignableFrom` roughly 5x FASTER than the JVM, at ~21ns, which
  * cannot account for the 1165µs-per-reference that synapify measured. So the 97x JVM/JS gap on
  * ResolutionPass is somewhere else.
  *
  * **The suspect.** `Definition.hashCode` (AST.scala:1009) is
  * {{{
  *   id.hashCode * 31 + loc.hashCode, then * 31 + getClass.hashCode
  * }}}
  * and `loc` is an `At`, a case class whose FIRST FIELD is `source: RiddlParserInput`
  * (At.scala:24). The concrete implementation `StringParserInput` (RiddlParserInput.scala:333) is
  * itself a case class whose first field is `data: String` — the entire text of the source file. So
  * the auto-generated hashCode chain ends up hashing the WHOLE SOURCE FILE on every
  * `Definition.hashCode` call.
  *
  * That is nearly free on the JVM, where `String.hashCode` memoises into the String's `hash` field
  * after the first call. A JS string cannot carry that field. If Scala.js recomputes the hash by
  * walking the characters each time, then every map operation keyed by a Definition re-hashes
  * hundreds of kilobytes of RIDDL source — which would be invisible on the JVM, invisible in
  * parsing (which never hashes Definitions), and devastating in exactly the map-heavy passes where
  * the gap actually appears.
  *
  * This benchmark tests that directly, and is written so the JVM and JS numbers are directly
  * comparable. Reports numbers, asserts no threshold.
  */
class DefinitionHashCostBenchmark extends AbstractTestingBasis {

  private val reps: Int = 20
  private val warmupReps: Int = 5

  /** Big enough that hashing the whole thing is visibly different from hashing a name. */
  private def modelSource: String =
    val sb = new StringBuilder
    sb.append("domain Benchmark is {\n")
    for c <- 0 until 60 do
      sb.append(s"  context C$c is {\n")
      for t <- 0 until 30 do
        sb.append(
          s"    type T${c}_$t is String with { described as \"padding to grow the source\" }\n"
        )
      end for
      sb.append("  }\n")
    end for
    sb.append("}\n")
    sb.toString
  end modelSource

  private def collectDefinitions(root: Root): Array[Definition] =
    val buf = mutable.ArrayBuffer.empty[Definition]
    def walk(v: RiddlValue): Unit =
      v match
        case d: Definition => buf += d
        case _             => ()
      end match
      v match
        case c: Container[?] => c.contents.toSeq.foreach(walk)
        case _               => ()
      end match
    end walk
    walk(root)
    buf.toArray
  end collectDefinitions

  private def measure(op: => Int): Double =
    var i = 0
    var acc = 0
    while i < warmupReps do
      acc += op
      i += 1
    end while
    val start = System.nanoTime()
    i = 0
    while i < reps do
      acc += op
      i += 1
    end while
    val ms = (System.nanoTime() - start) / 1_000_000.0
    if acc == Int.MinValue then info("unreachable") // keep `acc` live
    ms
  end measure

  "Definition hashing cost" should {
    "measure what a HashMap[Definition, _] operation actually pays" in {
      val source = modelSource
      val rpi = RiddlParserInput(source, "definition-hash-benchmark")
      val root = TopLevelParser.parseInput(rpi, false) match
        case Right(r)       => r
        case Left(messages) => fail(s"benchmark model failed to parse:\n${messages.format}")
      end root

      val defs = collectDefinitions(root)
      val shortName = "SomeDefinitionName"

      info(s"source: ${source.length} chars")
      info(s"definitions: ${defs.length}")
      info(s"sweeps: $reps measured, $warmupReps warmup")
      info(s"hashCode calls per sweep: ${defs.length}")
      info("")

      def sweepDefinitionHash(): Int =
        var n = 0
        var i = 0
        while i < defs.length do
          n += defs(i).hashCode
          i += 1
        end while
        n
      end sweepDefinitionHash

      def sweepSourceStringHash(): Int =
        var n = 0
        var i = 0
        while i < defs.length do
          n += source.hashCode
          i += 1
        end while
        n
      end sweepSourceStringHash

      def sweepShortStringHash(): Int =
        var n = 0
        var i = 0
        while i < defs.length do
          n += shortName.hashCode
          i += 1
        end while
        n
      end sweepShortStringHash

      // The realistic workload: build the kind of map Usages keeps.
      def sweepHashMapPuts(): Int =
        val m = mutable.HashMap.empty[Definition, Int]
        var i = 0
        while i < defs.length do
          m.put(defs(i), i)
          i += 1
        end while
        m.size
      end sweepHashMapPuts

      val defHashMs = measure(sweepDefinitionHash())
      val srcHashMs = measure(sweepSourceStringHash())
      val shortHashMs = measure(sweepShortStringHash())
      val mapPutMs = measure(sweepHashMapPuts())

      val perCall = (ms: Double) => ms * 1_000_000.0 / (reps.toDouble * defs.length)

      info(f"${"operation"}%-38s ${"ms"}%9s ${"ns/call"}%12s")
      info(f"${"Definition.hashCode"}%-38s $defHashMs%9.1f ${perCall(defHashMs)}%11.0f")
      // CONTROL, not a subject: this hashes the raw java.lang.String, which the parser-input
      // memoisation deliberately does NOT change. It stays slow on Scala.js and that is the point
      // — it is the underlying platform asymmetry, now paid once per file instead of per lookup.
      info(f"${"raw String.hashCode (CONTROL)"}%-38s $srcHashMs%9.1f ${perCall(srcHashMs)}%11.0f")
      info(
        f"${"shortName.hashCode (18 chars)"}%-38s $shortHashMs%9.1f ${perCall(shortHashMs)}%11.0f"
      )
      info(f"${"HashMap[Definition,_].put"}%-38s $mapPutMs%9.1f ${perCall(mapPutMs)}%11.0f")
      info("")
      if shortHashMs > 0 then
        info(f"whole-file hash costs ${srcHashMs / shortHashMs}%.1fx a short-name hash")
      end if

      defs.length must be > 0
      defHashMs must be >= 0.0
    }
  }
}
