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

/** Fidelity regression over the whole riddl-models corpus. For every model it runs two round-trips:
  *
  *   1. JSON-identity (the strong check): `root0 -> json1 -> root1 -> json2`, asserting `json1 ==
  *      json2`. If the serializer drops or reorders anything, the second JSON diverges from the
  *      first, so a stable fixed-point proves the AST<->JSON mapping is lossless and deterministic.
  *      2. Validation-parity (the weak check): the re-parsed AST introduces no new validation
  *         errors vs the original.
  *
  * **This suite GATES.** It was expected-red by standing policy while `../riddl-models` was being
  * migrated to 2.0 syntax; that migration is done, the corpus parses 189/189 and round-trips
  * identically 189/189, so the exemption is lifted. It reads a live sibling checkout, so a failure
  * here can mean either a regression in this repository OR a corpus that has drifted — read the
  * reported model names before assuming which. The in-repo counterpart is `Root2JsonFixturesTest`;
  * a cross-platform idempotence check on inline models lives in the shared `JsonRoundTripTest`.
  *
  * It is driven from RIDDL SOURCE, not from the checked-in `.bast` artifacts. Those artifacts carry
  * whatever `FORMAT_REVISION` was current when they were written, and the reader rejects any
  * mismatch — so as soon as the format moved, every read failed, every failure was skipped, and
  * both assertions below quietly reduced to `0 mustBe 0`. The suite passed for months without
  * checking anything. Driving from source removes the dependency on artifacts that have to be
  * regenerated anyway, and a parse failure is now reported rather than skipped.
  */
class Root2JsonCorpusTest extends AnyWordSpec with Matchers {

  /** The corpus entry points: each `<name>.conf` names its sibling `<name>.riddl` as `input-file`,
    * so the sibling is the model. Everything else under the tree is an include fragment.
    */
  private def modelFiles: Seq[File] =
    val root = new File("../riddl-models")
    def walk(f: File): Seq[File] =
      if f.isDirectory then Option(f.listFiles).map(_.toSeq).getOrElse(Nil).flatMap(walk)
      else if f.getName.endsWith(".conf") then
        val model = new File(f.getPath.stripSuffix(".conf") + ".riddl")
        if model.isFile then Seq(model) else Nil
      else Nil
    if root.isDirectory then walk(root).sortBy(_.getPath) else Nil
  end modelFiles

  private def read(f: File): String =
    val s = Source.fromFile(f)
    try s.mkString
    finally s.close()
  end read

  /** Parse a model with its ABSOLUTE path as the origin, so `include` resolves against the model's
    * own directory (`RiddlLib.originToURL` only builds a full-path URL for an origin starting "/").
    */
  private def parseModel(f: File): RiddlResult[com.ossuminc.riddl.language.AST.Root] =
    RiddlLib.parseString(read(f), f.getAbsolutePath)

  /** Collapse names/paths/quotes so error categories aggregate. */
  private def normalize(e: String): String =
    e.replaceAll("'[^']*'", "'X'")
      .replaceAll("\"[^\"]*\"", "\"X\"")
      .replaceAll("\\s+", " ")
      .trim
      .take(260)

  /** First line index where two JSON strings differ, with a short excerpt. */
  private def firstDiff(a: String, b: String): String =
    val la = a.linesIterator.toIndexedSeq
    val lb = b.linesIterator.toIndexedSeq
    val i = la.indices.find(i => i >= lb.size || la(i) != lb(i)).getOrElse(la.size)
    val ja = if i < la.size then la(i) else "<eof>"
    val jb = if i < lb.size then lb(i) else "<eof>"
    s"line $i:\n    json1: ${ja.trim.take(120)}\n    json2: ${jb.trim.take(120)}"

  "root2Json over the riddl-models corpus" should {

    "produce byte-identical JSON on the second round-trip (json1 == json2)" in {
      val files = modelFiles
      if files.isEmpty then cancel("../riddl-models corpus not found relative to the build root")

      var parsed = 0
      var reparsed = 0
      var identical = 0
      val unparsed = scala.collection.mutable.ListBuffer.empty[String]
      val mismatches = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        parseModel(f) match
          case RiddlResult.Success(root0) =>
            parsed += 1
            val json1 = RiddlLib.root2Json(root0)
            RiddlLib.parseJson(json1, f.getName) match
              case RiddlResult.Success(root1) =>
                reparsed += 1
                val json2 = RiddlLib.root2Json(root1)
                if json1 == json2 then identical += 1
                else mismatches += s"${f.getName}: ${firstDiff(json1, json2)}"
              case RiddlResult.Failure(errors) =>
                // Record WHY. A bare "[reparse-fail]" says a model broke without saying how, which
                // is useless the moment this suite gates rather than being expected-red.
                mismatches +=
                  s"${f.getName} [reparse-fail]: ${errors.take(2).map(_.format).mkString("; ")}"
          case RiddlResult.Failure(errors) =>
            unparsed += s"${f.getName}: ${errors.take(1).map(_.format).mkString}"
      end for
      val pct = if files.nonEmpty then 100.0 * identical / files.size else 0.0
      info(
        f"json-identity: models=${files.size} parsed=$parsed reparsed=$reparsed identical=$identical ($pct%.1f%%)"
      )
      if unparsed.nonEmpty then
        info(s"models that did not parse (${unparsed.size}, corpus migration pending):")
        unparsed.take(8).foreach(m => info("  " + m))
      end if
      if mismatches.nonEmpty then
        info(s"mismatches (${mismatches.size}, first 8):")
        mismatches.take(8).foreach(m => info("  " + m))
      end if

      // The AST<->JSON mapping must be a lossless, deterministic fixed point for the whole corpus.
      // Every model must parse, and every parsed model must round-trip identically — no skips.
      identical mustBe reparsed
      reparsed mustBe parsed
      parsed mustBe files.size
    }

    "introduce no new validation errors on the re-parsed AST (EVERY model)" in {
      val files = modelFiles
      if files.isEmpty then cancel("../riddl-models corpus not found relative to the build root")

      var reparsed = 0
      var clean = 0
      val newErrs = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
      val failedFiles = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        parseModel(f) match
          case RiddlResult.Success(root) =>
            val base = RiddlLib.validateRoot(root).errors.map(_.format).toSet
            val json = RiddlLib.root2Json(root)
            RiddlLib.parseJson(json, f.getName) match
              case RiddlResult.Success(root2) =>
                reparsed += 1
                val after = RiddlLib.validateRoot(root2).errors.map(_.format).toSet
                val added = after -- base
                if added.isEmpty then clean += 1
                else
                  failedFiles += f.getName
                  added.foreach(e => newErrs(normalize(e)) += 1)
              case RiddlResult.Failure(_) => failedFiles += (f.getName + " [reparse-fail]")
          case RiddlResult.Failure(_) => failedFiles += (f.getName + " [parse-fail]")
      end for
      // Reported as a COUNT, not a percentage. The percentage that used to print here read as a
      // score against a threshold, and there is no threshold: Reid ruled 2026-08-16 that the
      // corpus at 100% is the release gate and that ">= 95%" is contrived. A figure of 98.9%
      // invited exactly the misreading it produced -- it clears a bar that does not exist while
      // failing the assertion below, and BACKLOG repeated the 95% for weeks as though it were real.
      info(
        s"validation-parity: models=${files.size} reparsed=$reparsed cleanRoundTrip=$clean " +
          s"(gate: cleanRoundTrip must equal reparsed)"
      )
      info("top new-error categories (count, normalized message):")
      newErrs.toSeq.sortBy(-_._2).take(15).foreach { case (msg, n) => info(f"  $n%4d  $msg") }
      info("failed models: " + failedFiles.take(12).mkString(", "))

      // NO allowance. The one model that used to differ, `api-management.riddl`, was not a
      // fidelity problem at all: `checkPortletCardinality` keyed its counting map by Definition
      // VALUE, and since `Definition.equals` includes `loc`, two distinct same-named ports collapse
      // into one key on a tree that has no locations. That check counts by identity now.
      withClue(s"models introducing new validation errors: ${failedFiles.mkString(", ")}: ") {
        clean mustBe reparsed
      }
    }
  }
}
