/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.AbstractTestingBasis

/** A rule id is API: once published, a code means the same thing forever.
  *
  * These tests are the CODE mechanism behind that promise. Documentation saying "do not reuse a
  * code" is what the predecessor had, and it is not what stopped the two bugs recorded below.
  */
class RuleIdTest extends AbstractTestingBasis {

  private val codes: Seq[String] = RuleId.values.map(_.code).toSeq

  "RuleId" should {

    "have no duplicate codes" in {
      val dupes = codes.groupBy(identity).filter(_._2.size > 1).keys.toSeq.sorted
      dupes mustBe empty
    }

    "never reuse a retired code for a live rule" in {
      // Retiring is free; reusing is not. A consumer suppressing a retired code, or a migration
      // script keying on it, would silently change meaning if the code came back attached to
      // something else.
      val reused = codes.filter(RuleId.retired.contains).sorted
      reused mustBe empty
    }

    "give every code a known subject prefix" in {
      // Closed vocabulary, so the prefix set cannot drift one rule at a time. The twelve
      // grandfathered codes predate the scheme and are exempt -- see RuleId.grandfathered.
      val bad = codes
        .filterNot(RuleId.grandfathered.contains)
        .filterNot(c => RuleId.subjects.contains(c.takeWhile(_ != '-')))
        .sorted
      bad mustBe empty
    }

    "spell every code in kebab-case" in {
      val bad = codes.filterNot(_.matches("[a-z0-9]+(-[a-z0-9]+)+")).sorted
      bad mustBe empty
    }

    "round-trip every code through parse" in {
      RuleId.values.foreach { r => RuleId.parse(r.code) mustBe Some(r) }
      RuleId.parse("no-such-rule-exists") mustBe None
    }

    "derive the deprecation list rather than maintaining one" in {
      // The REGRESSION this guards. `DeprecationCode` kept a hand-written `all: Seq[String]`
      // beside the definitions, and TWICE a code was defined but never added to it --
      // `entity-option-to-intention` for months -- so migration reports that called themselves
      // exhaustive silently omitted a whole family. Deriving from `values` removes the second
      // list; this asserts the two can no longer disagree.
      val declared = RuleId.values.filter(_.deprecates).map(_.code).toSet
      Messages.DeprecationCode.all.toSet mustBe declared
      Messages.DeprecationCode.all.size mustBe declared.size // no duplicates smuggled in
    }

    "expose a mechanical fix only where the fix is a pure span replacement" in {
      // Claiming a mechanical fix that is NOT a span replacement corrupts source, so the map is
      // deliberately a subset of the auto-fixable set.
      RuleId.mechanicalReplacements.keySet must contain("prompt-statement")
      RuleId.mechanicalReplacements("prompt-statement") mustBe "do"
      RuleId.mechanicalReplacements.keySet mustNot contain("shape-keyword")
    }
  }

  "a Message carrying a rule" should {

    "keep the id OUT of `format`" in {
      // The platform-independent half of the contract, and the one that matters most here:
      // `format` is what CheckMessagesTest compares its goldens against, so the id must NOT be
      // there. That the LOGGER adds it is asserted in RuleIdLogRenderingTest, which is
      // jvm-native because `withLogger` cannot capture on JS -- see that suite.
      val m = Messages.Message(At.empty, "something is wrong", Messages.Error,
        ruleId = Some(RuleId.FieldDuplicateName))
      m.format mustNot include("[field-duplicate-name]")
      m.ruleCode mustBe Some("field-duplicate-name")
    }

    "leave a message with no rule unchanged either way" in {
      val m = Messages.Message(At.empty, "something is wrong", Messages.Error)
      m.format mustNot include("[")
      m.ruleCode mustBe None
    }

    "report deprecationCode only for an actual deprecation" in {
      // The compatibility accessor means "deprecations" to its callers. A rule id is now attached
      // to messages of every kind, so returning it unconditionally would silently widen what a
      // caller filtering on deprecations receives.
      val err = Messages.Message(At.empty, "x", Messages.Error, ruleId = Some(RuleId.FieldDuplicateName))
      val dep = Messages.Message(At.empty, "x", Messages.Deprecation, ruleId = Some(RuleId.DoStatement))
      err.deprecationCode mustBe None
      dep.deprecationCode mustBe Some("prompt-statement")
    }
  }
}
