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

import scala.reflect.{ClassTag, classTag}

/** Isolates the cost of the runtime type test that saturates ResolutionPass, on every platform.
  *
  * **Why this exists.** `PassCostBenchmark` (JVM) put Resolution at 44.6ms for 3730 references —
  * 0.18x the parse — while synapify measured it at 4344ms on Scala.js, 5.3x the parse. Parse itself
  * is only ~3.2x slower on Scala.js, so the ~97x gap on Resolution is not general runtime overhead;
  * something the pass does is specifically hostile to a JIT-less runtime. The prime suspect is
  * `ResolutionPass.isSameKind` (ResolutionPass.scala:661):
  * {{{
  *   classTag[DEF].runtimeClass.isAssignableFrom(d.getClass)
  * }}}
  * a JVM intrinsic, but a reflective ancestor walk on Scala.js and Native.
  *
  * **What it measures.** Four ways to answer "is this node of kind T", over a real AST:
  *   - `current` — exactly the shape `isSameKind` uses today, `runtimeClass` fetched per call
  *   - `hoisted` — same, but `runtimeClass` lifted out of the loop (the cheapest possible fix)
  *   - `predicate` — a stored `WithIdentifier => Boolean` lambda, i.e. a type-test registry. **Adds
  *     no data to any node.**
  *   - `direct` — a literal `isInstanceOf[T]` against a fixed type: the floor, what the runtime can
  *     do when nothing is reflective
  *
  * Reports numbers, asserts no threshold — same reasoning as `PassCostBenchmark`. Run on all three:
  * {{{
  *   sbt "passes/testOnly *TypeTestCostBenchmark"
  *   sbt "passesJS/testOnly *TypeTestCostBenchmark"
  *   sbt "passesNative/testOnly *TypeTestCostBenchmark"
  * }}}
  */
class TypeTestCostBenchmark extends AbstractTestingBasis {

  /** Repetitions of the full sweep over the AST. */
  private val reps: Int = 200

  /** Sweeps discarded before measuring, so the JVM's JIT reaches steady state. Without this the JVM
    * would look artificially close to the JIT-less runtimes, which is the very difference under
    * study.
    */
  private val warmupReps: Int = 50

  /** A synthetic but real model: parsed by the real parser into real AST nodes, so the class
    * hierarchy depth the type test walks is the genuine one.
    */
  private def modelSource: String =
    val sb = new StringBuilder
    sb.append("domain Benchmark is {\n")
    for c <- 0 until 40 do
      sb.append(s"  context C$c is {\n")
      for t <- 0 until 20 do sb.append(s"    type T${c}_$t is String\n")
      end for
      sb.append("  }\n")
    end for
    sb.append("}\n")
    sb.toString
  end modelSource

  /** Every definition in the model, flattened. Collected by a plain recursive walk rather than
    * `Finder`, because `Finder` itself type-tests and would contaminate the measurement.
    */
  private def collectDefinitions(root: Root): Array[WithIdentifier] =
    val buf = scala.collection.mutable.ArrayBuffer.empty[WithIdentifier]
    def walk(v: RiddlValue): Unit =
      v match
        case wi: WithIdentifier => buf += wi
        case _                  => ()
      end match
      v match
        case c: Container[?] => c.contents.toSeq.foreach(walk)
        case _               => ()
      end match
    end walk
    walk(root)
    buf.toArray
  end collectDefinitions

  // ---- the four strategies -------------------------------------------------

  /** Exactly what `ResolutionPass.isSameKind` does today. */
  private def countCurrent[T: ClassTag](ds: Array[WithIdentifier]): Int =
    var n = 0
    var i = 0
    while i < ds.length do
      if classTag[T].runtimeClass.isAssignableFrom(ds(i).getClass) then n += 1
      i += 1
    end while
    n
  end countCurrent

  /** Same test, `runtimeClass` resolved once. */
  private def countHoisted[T: ClassTag](ds: Array[WithIdentifier]): Int =
    val rc = classTag[T].runtimeClass
    var n = 0
    var i = 0
    while i < ds.length do
      if rc.isAssignableFrom(ds(i).getClass) then n += 1
      i += 1
    end while
    n
  end countHoisted

  /** A stored type-test lambda — a registry keyed by kind, adding nothing to any node. */
  private def countPredicate(ds: Array[WithIdentifier], p: WithIdentifier => Boolean): Int =
    var n = 0
    var i = 0
    while i < ds.length do
      if p(ds(i)) then n += 1
      i += 1
    end while
    n
  end countPredicate

  /** The floor: a monomorphic `isInstanceOf` the compiler can emit directly. */
  private def countDirect(ds: Array[WithIdentifier]): Int =
    var n = 0
    var i = 0
    while i < ds.length do
      if ds(i).isInstanceOf[Type] then n += 1
      i += 1
    end while
    n
  end countDirect

  // ---- harness -------------------------------------------------------------

  private def measure(label: String, ds: Array[WithIdentifier])(op: => Int): (String, Double, Int) =
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
    (label, ms, acc)
  end measure

  "Type-test cost" should {
    "compare ClassTag dispatch against the alternatives" in {
      val rpi = RiddlParserInput(modelSource, "type-test-benchmark")
      val root = TopLevelParser.parseInput(rpi, false) match
        case Right(r)       => r
        case Left(messages) => fail(s"benchmark model failed to parse:\n${messages.format}")
      end root

      val defs = collectDefinitions(root)
      info(s"definitions in model: ${defs.length}")
      info(s"sweeps: $reps measured, $warmupReps warmup")
      info(s"type tests per strategy: ${defs.length.toLong * reps}")
      info("")

      val predicate: WithIdentifier => Boolean = (d: WithIdentifier) => d.isInstanceOf[Type]

      val results = Seq(
        measure("current (classTag per call)", defs)(countCurrent[Type](defs)),
        measure("hoisted (runtimeClass once)", defs)(countHoisted[Type](defs)),
        measure("predicate (stored lambda)", defs)(countPredicate(defs, predicate)),
        measure("direct (isInstanceOf)", defs)(countDirect(defs))
      )

      // Every strategy must agree, or we are timing four different questions.
      val counts = results.map(_._3).distinct
      withClue(s"strategies disagreed on the answer: ${results.map(r => r._1 -> r._3)}") {
        counts.length mustBe 1
      }

      val floor = results.last._2
      info(f"${"strategy"}%-30s ${"ms"}%9s ${"vs direct"}%11s")
      for (label, ms, _) <- results do
        val ratio = if floor > 0 then ms / floor else 0.0
        info(f"$label%-30s $ms%9.1f $ratio%10.1fx")
      end for

      results.map(_._2).forall(_ >= 0.0) mustBe true
    }
  }
}
