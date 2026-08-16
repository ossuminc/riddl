/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** CHARACTERIZATION of BACKLOG § 1's "JsonModel's reader never rejects unknown/misspelled keys".
  *
  * These cases assert what happens TODAY, which is that an unknown key is silently dropped. They
  * are written to go RED the moment that changes, so whichever strictness the ruling picks, the
  * behaviour is pinned rather than assumed — and so the cost of the current behaviour is visible
  * as executable fact rather than as a paragraph in a backlog entry.
  *
  * The finding that matters for the design: the drop happens at BOTH reader layers, and they are
  * not the same mechanism.
  *
  *   - Hand-written readers (`readStatement`, `readValue`, `readTypeExpr`, …) build their result
  *     from selective `m("key")` / `m.get("key")` lookups against a `ujson.Obj`, with no step that
  *     diffs keys PRESENT against keys CONSUMED.
  *   - Every other DTO is read by upickle's derived `macroRW`, which ignores unknown keys by
  *     construction and is not ours to change.
  *
  * The backlog entry proposes "a shared consumed-keys tracker wrapping ujson.Obj, most likely".
  * That would fix the first layer and CANNOT fix the second — a wrapper sees the lookups a
  * hand-written reader makes, not the ones a derived reader makes internally. So the mechanism is
  * a more open question than the entry assumed. See the entry for the two decisions this needs.
  */
class JsonUnknownKeyTest extends AnyWordSpec with Matchers {

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

    "TODAY: silently accept a misspelled key on a macro-derived reader" in {
      // `breif` is dropped, so the domain simply has no brief. No diagnostic of any kind.
      parses(misspelledOnDerivedReader) mustBe true
    }

    "TODAY: silently accept an obsolete key on a hand-written reader" in {
      parses(obsoleteOnHandWrittenReader) mustBe true
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
