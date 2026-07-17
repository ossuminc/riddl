/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.passes.{
  OutlineEntry, OutlineOutput, OutlinePass, Pass, PassInput
}
import com.ossuminc.riddl.passes.stats.{StatsOutput, StatsPass}
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable

/** The per-kind portion of a [[RootSimilarity]]: how the definitions of one
  * `kind` (e.g. `Context`, `Entity`, `Command`) in root `a` compare with those
  * in root `b`.
  *
  * @param kind
  *   The definition kind string (`Definition.kind`), which already separates
  *   the message taxonomy (Command/Event/Query/Result/Record) via the `Type`
  *   kind override.
  * @param countA
  *   Number of definitions of this kind in root `a`.
  * @param countB
  *   Number of definitions of this kind in root `b`.
  * @param matched
  *   Fuzzy-matched `(a-name, b-name)` pairs (location- and case-independent).
  * @param unmatchedA
  *   Names of this kind in `a` with no fuzzy counterpart in `b`.
  * @param unmatchedB
  *   Names of this kind in `b` with no fuzzy counterpart in `a`.
  * @param score
  *   Per-kind similarity in `[0.0, 1.0]` blending count comparability and name
  *   overlap.
  */
case class KindComparison(
  kind: String,
  countA: Int,
  countB: Int,
  matched: Seq[(String, String)],
  unmatchedA: Seq[String],
  unmatchedB: Seq[String],
  score: Double
)

/** A deterministic, model-free structural similarity between two RIDDL `Root`
  * ASTs. Produced by [[RootComparison.compareRoots]].
  *
  * This is a pure-AST heuristic: it matches on `kind` + fuzzy name, never on
  * `Definition.equals`/`hashCode` (which include `loc` and therefore compare
  * structurally-identical definitions at different source locations as
  * unequal). No inference/embedding is involved.
  *
  * @param perKind
  *   One [[KindComparison]] per definition kind present in either root, sorted
  *   by kind name.
  * @param depthA
  *   Maximum nesting depth of root `a` (from `StatsPass.maximum_depth`).
  * @param depthB
  *   Maximum nesting depth of root `b`.
  * @param breadthA
  *   Number of top-level (shallowest) named containers in root `a`.
  * @param breadthB
  *   Number of top-level named containers in root `b`.
  * @param score
  *   Overall weighted similarity in `[0.0, 1.0]`; `1.0` for identical inputs.
  */
case class RootSimilarity(
  perKind: Seq[KindComparison],
  depthA: Int,
  depthB: Int,
  breadthA: Int,
  breadthB: Int,
  score: Double
) {

  /** Per-kind counts as `kind -> (countA, countB)`, as the task specifies. */
  def counts: Map[String, (Int, Int)] =
    perKind.map(kc => kc.kind -> (kc.countA, kc.countB)).toMap
}

/** Computes a structural similarity / diff between two RIDDL `Root` ASTs.
  *
  * Native-safe (pure Scala; no reflection, regex, or java formatting) and
  * deterministic. The count/name features are extracted with [[OutlinePass]]
  * (which records both container and leaf definitions, so leaf kinds such as
  * `Invariant` and `Constant` are included — unlike `StatsPass`, which only
  * tallies `Branch` definitions); the depth metric is taken from
  * `StatsPass.maximum_depth`.
  *
  * ==Scoring==
  * Per kind: `0.6 * countComparability + 0.4 * nameOverlap`, where
  * `countComparability = min(cA,cB)/max(cA,cB)` and
  * `nameOverlap = 2*matched/(cA+cB)` (Dice over fuzzy-matched names).
  *
  * Overall: `0.7 * weightedMean(perKind) + 0.3 * countVectorCosine`. The
  * weighted mean weights DDD-structural kinds above incidental ones
  * (Context/Entity = 3, message types = 2, other vital definitions = 1.5,
  * everything else = 1). The cosine term rewards two models with the same
  * count "shape" even when every name was changed, so a renamed-but-
  * structurally-equal model still scores high on structure. Identical roots
  * score exactly `1.0`.
  */
object RootComparison {

  /** Similarity weights per definition kind for the overall weighted mean.
    * DDD-structural kinds dominate; unlisted kinds use [[defaultWeight]].
    */
  private val weights: Map[String, Double] = Map(
    "Context" -> 3.0,
    "Entity" -> 3.0,
    "Command" -> 2.0,
    "Event" -> 2.0,
    "Query" -> 2.0,
    "Result" -> 2.0,
    "Record" -> 2.0,
    "Domain" -> 1.5,
    "Handler" -> 1.5,
    "Repository" -> 1.5,
    "Projector" -> 1.5,
    "Streamlet" -> 1.5,
    "Saga" -> 1.5,
    "Function" -> 1.5,
    "Adaptor" -> 1.5,
    "Epic" -> 1.5
  )

  private val defaultWeight: Double = 1.0

  /** Minimum normalized name similarity for two names to be a fuzzy match. */
  private val nameMatchThreshold: Double = 0.6

  /** Top-level AST wrapper kinds that carry no discriminating signal (always
    * present exactly once) and would otherwise pollute the counts, name lists,
    * and breadth metric. Excluded from the comparison.
    */
  private val structuralWrapperKinds: Set[String] = Set("Root", "Nebula")

  /** Compare two `Root` ASTs, returning a [[RootSimilarity]]. */
  def compareRoots(a: Root, b: Root)(using PlatformContext): RootSimilarity = {
    val entriesA = outlineOf(a).filterNot(e => structuralWrapperKinds.contains(e.kind))
    val entriesB = outlineOf(b).filterNot(e => structuralWrapperKinds.contains(e.kind))
    val namesA = namesByKind(entriesA)
    val namesB = namesByKind(entriesB)
    val allKinds = (namesA.keySet ++ namesB.keySet).toSeq.sorted
    val perKind = allKinds.map { k =>
      compareKind(k, namesA.getOrElse(k, Seq.empty), namesB.getOrElse(k, Seq.empty))
    }
    RootSimilarity(
      perKind = perKind,
      depthA = depthOf(a),
      depthB = depthOf(b),
      breadthA = breadthOf(entriesA),
      breadthB = breadthOf(entriesB),
      score = overallScore(perKind)
    )
  }

  /** Render a [[RootSimilarity]] between two roots as a Markdown report. */
  def similarityMarkdown(a: Root, b: Root)(using PlatformContext): String =
    render(compareRoots(a, b))

  // ---- feature extraction -------------------------------------------------

  private def outlineOf(root: Root)(using PlatformContext): Seq[OutlineEntry] = {
    val result = Pass.runThesePasses(PassInput(root), Seq(OutlinePass.creator()))
    result.outputs
      .outputOf[OutlineOutput](OutlinePass.name)
      .map(_.entries)
      .getOrElse(Seq.empty)
  }

  private def depthOf(root: Root)(using PlatformContext): Int = {
    val result = Pass.runThesePasses(PassInput(root), Pass.informationPasses)
    result.outputs
      .outputOf[StatsOutput](StatsPass.name)
      .map(_.maximum_depth)
      .getOrElse(0)
  }

  private def namesByKind(entries: Seq[OutlineEntry]): Map[String, Seq[String]] =
    entries.groupBy(_.kind).map((k, es) => k -> es.map(_.id))

  private def breadthOf(entries: Seq[OutlineEntry]): Int =
    if entries.isEmpty then 0
    else
      val minDepth = entries.map(_.depth).min
      entries.count(_.depth == minDepth)
    end if

  // ---- per-kind comparison ------------------------------------------------

  private def compareKind(
    kind: String,
    aNames: Seq[String],
    bNames: Seq[String]
  ): KindComparison = {
    val (matched, unmatchedA, unmatchedB) = fuzzyMatch(aNames, bNames)
    val cA = aNames.size
    val cB = bNames.size
    val hi = math.max(cA, cB)
    val countComp = if hi == 0 then 1.0 else math.min(cA, cB).toDouble / hi
    val nameComp = if cA + cB == 0 then 1.0 else (2.0 * matched.size) / (cA + cB)
    val score = 0.6 * countComp + 0.4 * nameComp
    KindComparison(kind, cA, cB, matched, unmatchedA, unmatchedB, score)
  }

  /** Greedily pair names from `a` to names from `b` by best normalized
    * similarity above [[nameMatchThreshold]]. Deterministic: candidates are
    * ordered by descending score with index tie-breaks.
    */
  private def fuzzyMatch(
    aNames: Seq[String],
    bNames: Seq[String]
  ): (Seq[(String, String)], Seq[String], Seq[String]) = {
    val aIdx = aNames.zipWithIndex
    val bIdx = bNames.zipWithIndex
    val candidates =
      for
        (an, ai) <- aIdx
        (bn, bi) <- bIdx
        s = nameSimilarity(an, bn)
        if s >= nameMatchThreshold
      yield (s, ai, bi, an, bn)
    val sorted = candidates.sortBy(c => (-c._1, c._2, c._3))
    val usedA = mutable.Set.empty[Int]
    val usedB = mutable.Set.empty[Int]
    val matched = mutable.ListBuffer.empty[(String, String)]
    for (_, ai, bi, an, bn) <- sorted do
      if !usedA.contains(ai) && !usedB.contains(bi) then
        usedA += ai
        usedB += bi
        matched += (an -> bn)
      end if
    end for
    val unmatchedA = aIdx.filterNot((_, i) => usedA.contains(i)).map(_._1)
    val unmatchedB = bIdx.filterNot((_, i) => usedB.contains(i)).map(_._1)
    (matched.toSeq, unmatchedA, unmatchedB)
  }

  /** Location- and case-independent name similarity in `[0.0, 1.0]`, computed
    * on names normalized to lowercase alphanumerics via Levenshtein ratio.
    */
  private def nameSimilarity(a: String, b: String): Double = {
    val na = normalize(a)
    val nb = normalize(b)
    if na.isEmpty && nb.isEmpty then 1.0
    else if na.isEmpty || nb.isEmpty then 0.0
    else if na == nb then 1.0
    else
      val dist = levenshtein(na, nb)
      1.0 - dist.toDouble / math.max(na.length, nb.length)
    end if
  }

  private def normalize(s: String): String =
    s.toLowerCase.filter(c => c.isLetterOrDigit)

  /** Two-row Levenshtein edit distance. Native-safe (arrays only). */
  private def levenshtein(a: String, b: String): Int = {
    val m = a.length
    val n = b.length
    if m == 0 then n
    else if n == 0 then m
    else
      var prev = Array.tabulate(n + 1)(identity)
      var curr = new Array[Int](n + 1)
      var i = 0
      while i < m do
        curr(0) = i + 1
        var j = 0
        while j < n do
          val cost = if a.charAt(i) == b.charAt(j) then 0 else 1
          curr(j + 1) = math.min(
            math.min(curr(j) + 1, prev(j + 1) + 1),
            prev(j) + cost
          )
          j += 1
        end while
        val tmp = prev
        prev = curr
        curr = tmp
        i += 1
      end while
      prev(n)
    end if
  }

  // ---- scoring ------------------------------------------------------------

  private def weightOf(kind: String): Double = weights.getOrElse(kind, defaultWeight)

  private def overallScore(perKind: Seq[KindComparison]): Double =
    if perKind.isEmpty then 1.0
    else
      val wsum = perKind.map(kc => weightOf(kc.kind)).sum
      val weighted = perKind.map(kc => weightOf(kc.kind) * kc.score).sum / wsum
      val cosine = countCosine(perKind)
      0.7 * weighted + 0.3 * cosine
    end if

  private def countCosine(perKind: Seq[KindComparison]): Double = {
    val dot = perKind.map(kc => kc.countA.toDouble * kc.countB).sum
    val magA = math.sqrt(perKind.map(kc => kc.countA.toDouble * kc.countA).sum)
    val magB = math.sqrt(perKind.map(kc => kc.countB.toDouble * kc.countB).sum)
    if magA == 0.0 && magB == 0.0 then 1.0
    else if magA == 0.0 || magB == 0.0 then 0.0
    else dot / (magA * magB)
  }

  // ---- markdown rendering -------------------------------------------------

  /** Render a computed [[RootSimilarity]] as a Markdown report. */
  def render(sim: RootSimilarity): String = {
    val sb = new mutable.StringBuilder
    sb.append("# Root Similarity Report\n\n")
    sb.append("**Overall similarity score:** ").append(fmt3(sim.score)).append("\n\n")

    sb.append("## Structural metrics\n\n")
    sb.append("| Metric | A | B |\n|---|---:|---:|\n")
    sb.append(s"| Max depth | ${sim.depthA} | ${sim.depthB} |\n")
    sb.append(s"| Breadth (top-level) | ${sim.breadthA} | ${sim.breadthB} |\n\n")

    sb.append("## Per-kind counts\n\n")
    sb.append("| Kind | A | B | Matched | Score |\n|---|---:|---:|---:|---:|\n")
    for kc <- sim.perKind do
      sb.append(s"| ${kc.kind} | ${kc.countA} | ${kc.countB} | ${kc.matched.size} | ")
        .append(fmt3(kc.score))
        .append(" |\n")
    end for

    sb.append("\n## Name matching\n\n")
    for kc <- sim.perKind do
      sb.append(s"### ${kc.kind}\n")
      if kc.matched.nonEmpty then
        val rendered = kc.matched.map((x, y) => if x == y then x else s"$x ≈ $y")
        sb.append("- Matched: ").append(rendered.mkString(", ")).append("\n")
      end if
      if kc.unmatchedA.nonEmpty then
        sb.append("- Only in A: ").append(kc.unmatchedA.mkString(", ")).append("\n")
      end if
      if kc.unmatchedB.nonEmpty then
        sb.append("- Only in B: ").append(kc.unmatchedB.mkString(", ")).append("\n")
      end if
    end for
    sb.toString
  }

  /** Format a `[0,1]` score to 3 decimals without java.util.Formatter
    * (Native-safe). Scores are non-negative.
    */
  private def fmt3(d: Double): String = {
    val scaled = math.round(d * 1000.0)
    val whole = scaled / 1000
    val frac = (scaled % 1000).toInt
    val fracStr = (1000 + frac).toString.substring(1)
    s"$whole.$fracStr"
  }
}
