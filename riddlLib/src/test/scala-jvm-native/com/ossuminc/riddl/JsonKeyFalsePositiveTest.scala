/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{Await, URL, ec, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.jdk.StreamConverters.*

/** The unknown-key warning must not fire on VALID documents, measured against the largest body of
  * valid RIDDL JSON that exists: every corpus model, written out by our own writer and read back.
  *
  * This is not redundant with `JsonUnknownKeyVocabularyTest`, which checks the vocabulary against
  * the READER source. Two defects got past that guard and were caught only here, both because they
  * were invisible from the reader side:
  *
  *   - the writer emits SIGIL keys (`$kind`, `$at`) on every node, which no DTO field name or
  *     reader lookup spells — 188 of 188 models warned;
  *   - a `Schema`'s `data` and `links` are maps keyed by the MODELLER's identifiers, not by schema,
  *     so every `orders`/`tickets`/`campaigns` read as an unrecognized key — 184 of 188 warned. The
  *     claim that no such map existed came from grepping the readers for key iteration, which finds
  *     nothing, because upickle's derived `Map[String, _]` reader does the iterating.
  *
  * Both would have shipped a diagnostic that fires on every correct document, which is worse than
  * no diagnostic: it teaches authors to ignore the channel. The corpus round-trip suite cannot
  * catch this — it calls `parseJson`, which discards messages.
  */
class JsonKeyFalsePositiveTest extends AnyWordSpec with Matchers {

  "unknown-key warning" should {
    "not fire on JSON written from the corpus" in {
      val dir = Path.of("../riddl-models")
      if !Files.isDirectory(dir) then cancel("no corpus")
      val entries = Files
        .walk(dir)
        .toScala(Seq)
        .filter(p => p.getFileName.toString.endsWith(".riddl"))
        .filter(p =>
          p.getParent.getFileName.toString == p.getFileName.toString.stripSuffix(".riddl")
        )
        .sorted
      val offenders = mutable.ListBuffer.empty[String]
      var models = 0
      entries.foreach { p =>
        val rpi = Await.result(
          RiddlParserInput.fromURL(URL.fromFullPath(p.toAbsolutePath.toString)),
          60.seconds
        )
        TopLevelParser.parseInput(rpi).foreach { root =>
          models += 1
          val json = RiddlLib.root2Json(root)
          val warns = RiddlLib
            .parseJsonWithMessages(json, p.getFileName.toString)
            ._2
            .filter(_.message.contains("not recognized by any RIDDL reader"))
          warns.foreach { w =>
            val k = w.message.split("'").lift(1).getOrElse("?")
            offenders.append(k)
          }
        }
      }
      info(s"models=$models  distinct unrecognized keys=${offenders.distinct.size}")
      withClue(
        "keys the warning would fire on, by frequency: " +
          offenders
            .groupBy(identity)
            .toSeq
            .sortBy(-_._2.size)
            .take(20)
            .map { case (k, xs) => s"$k x${xs.size}" }
            .mkString(", ") + "\n"
      ) {
        models must be > 100 // a corpus that failed to load would satisfy the assertion below
        offenders.distinct mustBe empty
      }
    }
  }
}
