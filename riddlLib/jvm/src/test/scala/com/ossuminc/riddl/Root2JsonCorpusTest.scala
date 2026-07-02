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
import java.nio.file.Files

/** Fidelity regression over the whole riddl-models corpus. For every `.bast` model it runs two
  * round-trips:
  *
  *   1. JSON-identity (the strong check): `root0 -> json1 -> root1 -> json2`, asserting `json1 ==
  *      json2`. If the serializer drops or reorders anything, the second JSON diverges from the
  *      first, so a stable fixed-point proves the AST<->JSON mapping is lossless and deterministic.
  *      2. Validation-parity (the weak check): the re-parsed AST introduces no new validation
  *      errors vs the original.
  *
  * JVM-only (walks the `../riddl-models` directory tree); cancels if the corpus isn't present. A
  * cross-platform (JVM/JS/Native) idempotence check on inline models lives in the shared
  * `JsonRoundTripTest`.
  */
class Root2JsonCorpusTest extends AnyWordSpec with Matchers {

  private def bastFiles: Seq[File] =
    val root = new File("../riddl-models")
    def walk(f: File): Seq[File] =
      if f.isDirectory then Option(f.listFiles).map(_.toSeq).getOrElse(Nil).flatMap(walk)
      else if f.getName.endsWith(".bast") then Seq(f)
      else Nil
    if root.isDirectory then walk(root) else Nil

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
      val files = bastFiles
      if files.isEmpty then cancel("../riddl-models corpus not found relative to the build root")

      var read = 0
      var reparsed = 0
      var identical = 0
      val mismatches = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        val bytes = Files.readAllBytes(f.toPath)
        RiddlLib.bast2FlatAST(bytes) match
          case RiddlResult.Success(root0) =>
            read += 1
            val json1 = RiddlLib.root2Json(root0)
            RiddlLib.parseJson(json1, f.getName) match
              case RiddlResult.Success(root1) =>
                reparsed += 1
                val json2 = RiddlLib.root2Json(root1)
                if json1 == json2 then identical += 1
                else mismatches += s"${f.getName}: ${firstDiff(json1, json2)}"
              case RiddlResult.Failure(_) => mismatches += s"${f.getName} [reparse-fail]"
          case RiddlResult.Failure(_) => // bast read failure (skip)
      val pct = if files.nonEmpty then 100.0 * identical / files.size else 0.0
      info(
        f"json-identity: files=${files.size} bastRead=$read reparsed=$reparsed identical=$identical ($pct%.1f%%)"
      )
      if mismatches.nonEmpty then
        info("mismatches (first 8):")
        mismatches.take(8).foreach(m => info("  " + m))

      // The AST<->JSON mapping must be a lossless, deterministic fixed point for
      // the whole corpus. Currently 187/187; require identity for all read models.
      identical mustBe reparsed
      reparsed mustBe read
    }

    "introduce no new validation errors on the re-parsed AST (>= 95% of models)" in {
      val files = bastFiles
      if files.isEmpty then cancel("../riddl-models corpus not found relative to the build root")

      var reparsed = 0
      var clean = 0
      val newErrs = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
      val failedFiles = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        val bytes = Files.readAllBytes(f.toPath)
        RiddlLib.bast2FlatAST(bytes) match
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
          case RiddlResult.Failure(_) => // bast read failure (skip)
      val pct = if files.nonEmpty then 100.0 * clean / files.size else 0.0
      info(
        f"validation-parity: files=${files.size} reparsed=$reparsed cleanRoundTrip=$clean ($pct%.1f%%)"
      )
      info("top new-error categories (count, normalized message):")
      newErrs.toSeq.sortBy(-_._2).take(15).foreach { case (msg, n) => info(f"  $n%4d  $msg") }
      info("failed models: " + failedFiles.take(12).mkString(", "))

      // Currently 100% (187/187); floor guards regressions.
      pct must be >= 95.0
    }
  }
}
