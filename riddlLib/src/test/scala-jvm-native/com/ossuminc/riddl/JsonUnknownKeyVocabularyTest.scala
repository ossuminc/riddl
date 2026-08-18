/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.json.JsonModel
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** Keeps `JsonModel.knownKeys` honest by re-deriving it from `JsonModel.scala`'s own source.
  *
  * `knownKeys` drives the unknown-key warning, and a hand-maintained list guarding a 2,500-line
  * file is exactly the arrangement that drifts — the same shape as `KnownOptions` vs the option
  * registry, which drifted three times and produced spurious warnings on valid input every time.
  * Drift here is worse than spurious: a key missing from the set makes the warning fire on CORRECT
  * documents, which teaches authors to ignore it.
  *
  * The extraction mirrors what the readers actually accept, which is two disjoint things:
  *   - the string literals the HAND-WRITTEN readers look up (`m("x")`, `m.get("x")`, `obj("x")`)
  *   - the field names of the `*Dto` case classes, which is precisely what upickle's derived
  *     `macroRW` readers accept
  *
  * JVM-only because it reads a source file. That is acceptable for a guard: it constrains the
  * source, and the source is the same on every platform.
  */
class JsonUnknownKeyVocabularyTest extends AnyWordSpec with Matchers {

  private val source: Path =
    Path.of("riddlLib/src/main/scala/com/ossuminc/riddl/json/JsonModel.scala")

  "JsonModel.knownKeys" should {

    "match every key the readers accept, re-derived from the source" in {
      Files.isRegularFile(source) mustBe true
      // Comments are STRIPPED first. Without this the extraction reads prose as code: the very
      // scaladoc explaining this mechanism writes `m("key")` as an example, and the first run of
      // this guard duly reported a missing key named "key". A guard that analyses documentation
      // reports defects that do not exist, which costs exactly as much trust as missing real ones.
      val text = Files
        .readString(source)
        .replaceAll("""(?s)/\*.*?\*/""", " ")
        .replaceAll("""(?m)//.*$""", " ")

      // Keys the hand-written readers look up.
      val lookups: Set[String] =
        """(?:\bm|\bo|\bobj|\.obj)\s*(?:\.get)?\(\s*"([A-Za-z_][A-Za-z0-9_]*)"""".r
          .findAllMatchIn(text)
          .map(_.group(1))
          .toSet

      // Field names of the DTO case classes = what macroRW accepts.
      val fields: Set[String] =
        """(?s)case class \w+Dto\s*\(([^)]*)\)""".r
          .findAllMatchIn(text)
          .flatMap { m =>
            """(?:^|,)\s*([a-zA-Z_][A-Za-z0-9_]*)\s*:""".r
              .findAllMatchIn(m.group(1))
              .map(_.group(1))
          }
          .toSet

      // The SIGIL keys are written by the Jsonifier on every node and spelled by no DTO field or
      // reader lookup, so neither extraction above can see them. They are named explicitly rather
      // than pattern-matched: there are exactly two, and a regex loose enough to catch them would
      // also catch interpolated strings.
      val sigils = Set("$kind", "$at")
      val derived = lookups ++ fields ++ sigils
      derived must not be empty

      val missing = derived -- JsonModel.knownKeys
      val extra = JsonModel.knownKeys -- derived

      withClue(
        s"MISSING from knownKeys (the warning will fire on correct documents): ${missing.toSeq.sorted}\n" +
          s"EXTRA in knownKeys (an unknown key will pass unnoticed): ${extra.toSeq.sorted}\n"
      ) {
        missing mustBe empty
        extra mustBe empty
      }
    }
  }
}
