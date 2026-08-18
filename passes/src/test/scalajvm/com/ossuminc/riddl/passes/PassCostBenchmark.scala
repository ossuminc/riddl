/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.analysis.AnalysisPass
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.utils.{AbstractTestingBasis, Await, URL, pc}

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

/** Per-pass cost breakdown on a large model, on the JVM.
  *
  * This exists to answer a specific question: synapify measured ResolutionPass at 4344ms on
  * reactive-bbq (56% of analysis, 5.3x the parse) and asked whether resolution is algorithmically
  * expensive. **Their numbers are Scala.js.** Native measurement of the same model put the whole
  * `validate` at ~3.1x parse versus ~8.4x on Scala.js, so the headline is heavily
  * platform-amplified and the JVM shape has to be established before anything is "optimized".
  *
  * Deliberately NOT a gate:
  *   - Nothing here asserts a time threshold. `BASTPerformanceBenchmark` asserts `speedup > 1.0`
  *     and was observed going 0.9956x (red), then 13.0x, 9.3x, 6.1x back to back on one machine — a
  *     real effect compared against a number with more variance than headroom. That teaches people
  *     to re-run reds. This reports numbers and asserts only that the passes ran.
  *   - It CANCELS rather than fails when `../riddl-models` is absent, so CI without the corpus
  *     checked out stays green.
  *
  * Run it with:
  * {{{
  *   sbt "passes/testOnly *PassCostBenchmark"
  * }}}
  * (`testOnly`, not `test` — `test` resolves to `testQuick` and silently skips.)
  */
class PassCostBenchmark extends AbstractTestingBasis {

  /** Where the corpus lives when checked out beside this repo. */
  private val modelPath: Path =
    Path.of("../riddl-models/hospitality/food-service/reactive-bbq/reactive-bbq.riddl")

  /** Iterations discarded before measuring, to let the JIT reach steady state. The JVM needs this
    * far more than Scala.js does; measuring a cold JVM would overstate everything roughly equally
    * and tell us nothing about the RATIOS, which is the actual question.
    */
  private val warmups: Int = 2

  /** Measured iterations. The reported figure is the MEDIAN, not the mean — one GC pause during a
    * 4-second run moves a mean far more than it moves a median.
    */
  private val runs: Int = 5

  private def median(xs: Seq[Double]): Double =
    val sorted = xs.sorted
    val n = sorted.length
    if n == 0 then 0.0
    else if n % 2 == 1 then sorted(n / 2)
    else (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
    end if

  private def millisOf[T](block: => T): (Double, T) =
    val start = System.nanoTime()
    val result = block
    ((System.nanoTime() - start) / 1_000_000.0, result)

  private def parseModel(): (Double, Root) =
    val input = Await.result(
      RiddlParserInput.fromURL(URL.fromCwdPath(modelPath.toString), "pass-cost-benchmark"),
      60.seconds
    )
    val (ms, result) = millisOf(TopLevelParser.parseInput(input, false))
    result match
      case Right(root) => (ms, root)
      case Left(messages) =>
        fail(s"reactive-bbq failed to parse:\n${messages.map(_.format).mkString("\n")}")
    end match

  /** Run every analysis pass in order against one Root, timing each individually.
    *
    * This mirrors `Pass.runThesePasses` exactly — same creators, same order, same accumulating
    * `PassesOutput` — but times each pass separately. Passes depend on earlier passes' outputs, so
    * they cannot be run in isolation or reordered.
    */
  private def timeEachPass(root: Root): Seq[(String, Double)] =
    val input = PassInput(root)
    val outputs = PassesOutput()
    for creator <- AnalysisPass.analysisPasses yield
      val aPass = creator(input, outputs)
      val (ms, output) = millisOf(Pass.runPass[PassOutput](input, outputs, aPass))
      outputs.outputIs(aPass.name, output)
      aPass.name -> ms
    end for
  end timeEachPass

  "Pass cost on reactive-bbq (JVM)" should {
    "report a per-pass breakdown against parse" in {
      if !Files.isRegularFile(modelPath) then
        info(s"SKIPPED: no corpus at $modelPath — check out ../riddl-models to run this")
        cancel(s"$modelPath not present")
      end if

      val lineCount = Files.lines(modelPath).count()
      info(s"model: ${modelPath.getFileName} (entry point, $lineCount lines before includes)")

      for _ <- 1 to warmups do
        val (_, root) = parseModel()
        timeEachPass(root)
      end for

      val parseSamples = scala.collection.mutable.ArrayBuffer.empty[Double]
      val passSamples =
        scala.collection.mutable.LinkedHashMap
          .empty[String, scala.collection.mutable.ArrayBuffer[Double]]

      for _ <- 1 to runs do
        val (parseMs, root) = parseModel()
        parseSamples += parseMs
        for (name, ms) <- timeEachPass(root) do
          passSamples.getOrElseUpdate(name, scala.collection.mutable.ArrayBuffer.empty) += ms
        end for
      end for

      // Guard against the reading that makes this whole benchmark worthless: Resolution could
      // look cheap because it RESOLVED NOTHING. Report how many references it actually placed in
      // the refMap, so the cost is anchored to real work.
      val (_, probeRoot) = parseModel()
      val probeInput = PassInput(probeRoot)
      val probeOutputs = PassesOutput()
      Pass.runPass[PassOutput](probeInput, probeOutputs, SymbolsPass(probeInput, probeOutputs))
      val resolutionOutput =
        Pass.runPass[ResolutionOutput](
          probeInput,
          probeOutputs,
          ResolutionPass(probeInput, probeOutputs)
        )
      info(s"references resolved into refMap: ${resolutionOutput.refMap.size}")
      resolutionOutput.refMap.size must be > 0

      val parseMedian = median(parseSamples.toSeq)
      val passMedians = passSamples.map((name, xs) => name -> median(xs.toSeq)).toSeq
      val total = passMedians.map(_._2).sum

      info(f"parse (all includes): $parseMedian%.1f ms   [median of $runs, $warmups warmups]")
      info("")
      info(f"${"pass"}%-24s ${"ms"}%8s ${"share"}%8s ${"xParse"}%8s")
      for (name, ms) <- passMedians.sortBy(-_._2) do
        val share = if total > 0 then ms / total * 100 else 0.0
        val xParse = if parseMedian > 0 then ms / parseMedian else 0.0
        info(f"$name%-24s $ms%8.1f $share%7.1f%% $xParse%7.2fx")
      end for
      info(f"${"TOTAL"}%-24s $total%8.1f")
      info("")
      info(f"analysis total is ${total / parseMedian}%.2fx the parse")

      // Assert only that the run happened and produced every pass. No timing threshold —
      // see the class comment for why.
      passMedians.map(_._1) must contain allOf ("Symbols", "Resolution", "Validation")
      total must be > 0.0
    }
  }
}
