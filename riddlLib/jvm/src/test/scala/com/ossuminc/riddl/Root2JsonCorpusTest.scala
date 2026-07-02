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

/** Fidelity regression: round-trip the whole riddl-models corpus through
  * `bast2FlatAST -> validateRoot -> root2Json -> parseJson -> validateRoot` and
  * require that the round-trip introduces no new validation errors for the vast
  * majority of models. JVM-only; cancels if `../riddl-models` isn't present.
  *
  * Also prints a breakdown of new-error categories to guide fidelity work.
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
    e.replaceAll("'[^']*'", "'X'").replaceAll("\"[^\"]*\"", "\"X\"").replaceAll("\\s+", " ").trim.take(260)

  "root2Json over the riddl-models corpus" should {
    "round-trip with no new validation errors for the vast majority of models" in {
      val files = bastFiles
      if files.isEmpty then cancel("../riddl-models corpus not found relative to the build root")

      var read = 0
      var reparsed = 0
      var clean = 0
      val newErrs = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
      val failedFiles = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        val bytes = Files.readAllBytes(f.toPath)
        RiddlLib.bast2FlatAST(bytes) match
          case RiddlResult.Success(root) =>
            read += 1
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
      info(f"corpus: files=${files.size} bastRead=$read reparsed=$reparsed cleanRoundTrip=$clean ($pct%.1f%%)")
      info("top new-error categories (count, normalized message):")
      newErrs.toSeq.sortBy(-_._2).take(15).foreach { case (msg, n) => info(f"  $n%4d  $msg") }
      info("failed models: " + failedFiles.take(12).mkString(", "))

      // The AST->JSON->AST round-trip must not introduce new validation errors for
      // at least 95% of the corpus. Currently 100% (187/187); floor guards regressions.
      reparsed must be >= (files.size * 95 / 100)
      pct must be >= 95.0
    }
  }
}
