/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unknown/misspelled JSON keys now WARN (Reid's ruling, 2026-08-16).
  *
  * These cases began life as a characterization of the silent-drop behaviour, written before the
  * ruling so that whichever strictness was chosen would land against pinned behaviour rather than
  * an assumption. They now assert the warning, which is exactly what characterizing first buys: the
  * change of behaviour is visible as a diff in the assertions.
  *
  * Still ACCEPTED, not rejected — a Warning breaks no existing producer while making a typo visible
  * immediately, and the flip to Error can be decided later with evidence.
  *
  * The finding that shaped the mechanism: the drop happened at BOTH reader layers, and they are not
  * the same mechanism.
  *
  *   - Hand-written readers (`readStatement`, `readValue`, `readTypeExpr`, …) build their result
  *     from selective `m("key")` / `m.get("key")` lookups against a `ujson.Obj`, with no step that
  *     diffs keys PRESENT against keys CONSUMED.
  *   - Every other DTO is read by upickle's derived `macroRW`, which ignores unknown keys by
  *     construction and is not ours to change.
  *
  * The backlog entry proposed "a shared consumed-keys tracker wrapping ujson.Obj, most likely".
  * That would have fixed the first layer and could not have fixed the second — a wrapper sees the
  * lookups a hand-written reader makes, never the ones a derived reader makes internally. Hence the
  * shipped design: validate the raw tree against `JsonModel.knownKeys` BEFORE any reader runs,
  * which is the one place both layers are visible at once.
  */
class JsonUnknownKeyTest extends AnyWordSpec with Matchers {

  private def warningsFor(json: String): Seq[String] =
    RiddlLib
      .parseJsonWithMessages(json)
      ._2
      .filter(_.message.contains("not recognized by any RIDDL reader"))
      .map(_.message)
      .toSeq

  private def parses(json: String): Boolean =
    RiddlLib.parseJson(json) match
      case RiddlResult.Success(_) => true
      case RiddlResult.Failure(errors) =>
        info("parseJson reported: " + errors.map(_.format).mkString("; "))
        false

  /** A misspelling of a key a MACRO-DERIVED reader consumes: `brief` on a domain. */
  private val misspelledOnDerivedReader =
    """{ "domains": [ { "name": "D", "breif": "typo for brief",
      |  "contexts": [ { "name": "C" } ] } ] }""".stripMargin

  /** A key no reader has ever consumed, on a HAND-WRITTEN reader: `negated` on a `when` statement.
    * This is not hypothetical — `JSON_INPUT.md:255` documented exactly this field, correctly at the
    * time, and it kept being accepted-and-dropped after `WhenStatement.negated` was deleted.
    */
  private val obsoleteOnHandWrittenReader =
    """{ "domains": [ { "name": "D", "contexts": [ { "name": "C",
      |  "entities": [ { "name": "E", "state": { "name": "s", "recordType": "R" },
      |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "init", "statements": [
      |      { "kind": "when", "negated": true, "condition": "x > 0", "then": [ "do the then" ] }
      |    ] } ] } ] } ],
      |  "types": [ { "name": "R", "typeExpression": { "kind": "Record", "fields": [
      |    { "name": "n", "type": { "kind": "Integer" } } ] } } ] } ] } ] }""".stripMargin

  "JsonModel's readers" should {

    // Still ACCEPTED -- a Warning, not an Error, so no existing producer breaks -- but no longer
    // SILENT. These two cases previously asserted the silence; that they now assert the warning is
    // the whole point of having characterized the behaviour before changing it.
    "warn about a misspelled key on a macro-derived reader" in {
      parses(misspelledOnDerivedReader) mustBe true
      val w = warningsFor(misspelledOnDerivedReader)
      withClue(w.mkString("\n")) {
        w.exists(_.contains("'breif'")) mustBe true
      }
    }

    "warn about an obsolete key on a hand-written reader" in {
      parses(obsoleteOnHandWrittenReader) mustBe true
      val w = warningsFor(obsoleteOnHandWrittenReader)
      withClue(w.mkString("\n")) {
        w.exists(_.contains("'negated'")) mustBe true
      }
    }

    "say WHERE the unknown key is, not merely that there is one" in {
      // A document is a deep tree and the same key name may appear at many depths; a diagnostic
      // that named only the key would send the author hunting.
      warningsFor(misspelledOnDerivedReader).head must include("domains[0].breif")
    }

    "stay SILENT for a document whose keys are all recognized" in {
      // The false-positive guard. Without it, a vocabulary that had accidentally lost a legitimate
      // key would warn on correct documents and this suite would still be green.
      warningsFor(obsoleteOnHandWrittenReader.replace(""""negated": true, """, "")) mustBe empty
    }

    // The isolation control for the case above: the SAME document with the obsolete key removed.
    // Without this, a failure above cannot be attributed -- a malformed fixture and a rejected key
    // look identical from the outside.
    "control: the same document WITHOUT the obsolete key" in {
      parses(obsoleteOnHandWrittenReader.replace(""""negated": true,""", "")) mustBe true
    }

    // The control. Whatever strictness is chosen must not reject a document that merely OMITS an
    // optional key -- schema evolution in the other direction, and the constraint the entry names
    // explicitly ("old documents from before a field existed must still read").
    "keep accepting a document that omits an optional key" in {
      parses("""{ "domains": [ { "name": "D", "contexts": [ { "name": "C" } ] } ] }""") mustBe true
    }
  }
}
