/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.AbstractTestingBasis

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The append-only ledger of every rule id ever published.
  *
  * The other guarantees ([[RuleIdTest]]) hold within a single compilation: codes are unique, and no
  * LIVE code sits in `retired`. Neither can see a code that has been DELETED -- and a deleted code
  * is exactly the one at risk of being reused later by someone who has never heard of it.
  *
  * So the ledger lives on disk, is committed, and is append-only. Deleting a rule without retiring
  * its code fails here. JVM-only because it reads a file; the rules themselves are asserted
  * cross-platform in [[RuleIdTest]].
  */
class RuleIdSnapshotTest extends AbstractTestingBasis {

  private val ledgerPath = Path.of("language/src/test/resources/rule-ids.txt")

  private def ledger: Set[String] =
    Files
      .readAllLines(ledgerPath)
      .asScala
      .map(_.trim)
      .filter(l => l.nonEmpty && !l.startsWith("#"))
      .toSet

  "the rule-id ledger" should {

    "be present" in {
      // A missing ledger would make every assertion below vacuously pass -- the same shape as the
      // corpus suites that reduced to `0 mustBe 0` for months.
      Files.exists(ledgerPath) mustBe true
      ledger.size must be >= 27
    }

    "contain every live code" in {
      // Fails when a rule is ADDED without recording it. The fix is to append the code, which is
      // what makes the ledger a record of everything ever published rather than of what happens to
      // exist today.
      val live = RuleId.values.map(_.code).toSet
      val unrecorded = (live -- ledger).toSeq.sorted
      withClue(s"append these to $ledgerPath: ${unrecorded.mkString(", ")}\n") {
        unrecorded mustBe empty
      }
    }

    "account for every code it lists, as live or retired" in {
      // Fails when a rule is DELETED without retiring its code -- the case the in-memory checks
      // cannot see, and the one that lets a code be silently reused for a different rule later.
      val live = RuleId.values.map(_.code).toSet
      val unaccounted = (ledger -- live -- RuleId.retired).toSeq.sorted
      withClue(
        s"these left RuleId without being added to RuleId.retired: ${unaccounted.mkString(", ")}\n"
      ) {
        unaccounted mustBe empty
      }
    }
  }
}
