/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.AbstractTestingBasis

/** Unit tests for the consolidated option registry.
  *
  * [[RecognizedOptions.registry]] is the single source of truth for RIDDL options; the per-kind
  * lists in [[KnownOptions]] are DERIVED from it. Before the consolidation the two were maintained
  * by hand and drifted apart three times, each drift producing a spurious "not a recognized RIDDL
  * option" style warning on a perfectly valid option.
  *
  * The most important test here is the NO-SHRINK test: every derived list must still contain every
  * name that list carried by hand before the consolidation. If one is missing, the registry lacks
  * an entry — ADD the entry, do NOT weaken the baseline.
  */
class RecognizedOptionsTest extends AbstractTestingBasis {

  /** The exact contents of every `KnownOptions.<kind>` list immediately BEFORE the registry
    * consolidation, spelled as literal strings so that this baseline cannot drift with the code it
    * guards.
    *
    * ONE deliberate correction: the entity list used to hold `KnownOption.value`, and that constant
    * held the string `"final value"` — a typo (a bad edit of `final val value = "value"`) present
    * since the constant was introduced. A name with a space in it can never be a parseable RIDDL
    * option, and the constant had no consumers, so the typo was never observable. The baseline
    * below records the intended name, `"value"`.
    */
  private val baseline: Map[String, Seq[String]] = Map(
    "adaptor" -> Seq("technology", "kind", "css", "faicon"),
    "application" -> Seq("technology", "kind", "css", "faicon"),
    "connector" -> Seq("persistent", "technology", "kind"),
    "context" -> Seq(
      "wrapper",
      "gateway",
      "service",
      "package",
      "namespace",
      "technology",
      "css",
      "kind",
      "faicon",
      "protocol",
      "event_catalog_version",
      "sql_dialect",
      "backstage_owner",
      "backstage_lifecycle",
      "backstage_type"
    ),
    "domain" -> Seq(
      "external",
      "package",
      "namespace",
      "technology",
      "css",
      "kind",
      "faicon",
      "event_catalog_version",
      "sql_dialect",
      "backstage_owner",
      "backstage_lifecycle",
      "backstage_type",
      "confluence_space",
      "confluence_parent"
    ),
    "entity" -> Seq(
      "event-sourced",
      "value",
      "aggregate",
      "transient",
      "consistent",
      "available",
      "finite-state-machine",
      "kind",
      "message-queue",
      "technology",
      "css",
      "faicon",
      "protocol",
      "sql_dialect",
      "sql_table",
      "backstage_owner",
      "backstage_lifecycle",
      "backstage_type"
    ),
    "epic" -> Seq("technology", "css", "sync", "kind", "faicon"),
    "projector" -> Seq(
      "technology",
      "css",
      "faicon",
      "kind",
      "protocol",
      "backstage_owner",
      "backstage_lifecycle",
      "backstage_type"
    ),
    "repository" -> Seq(
      "technology",
      "kind",
      "css",
      "faicon",
      "protocol",
      "sql_dialect",
      "sql_table",
      "backstage_owner",
      "backstage_lifecycle",
      "backstage_type"
    ),
    "saga" -> Seq(
      "technology",
      "kind",
      "css",
      "faicon",
      "protocol",
      "parallel"
      // `compensate` was in this baseline and is DELIBERATELY removed. It was registered as a
      // saga option declaring that failure runs the steps' undo blocks in reverse, but
      // `SagaParser` requires `reverted by` on EVERY step, so a saga without compensation
      // cannot be written and the option distinguished nothing. This ratchet exists to catch
      // ACCIDENTAL loss, so the baseline moves only with a reason recorded -- never to make a
      // red run go green.
    ),
    "streamlet" -> Seq("technology", "css", "kind", "protocol"),
    "portlet" -> Seq("async")
  )

  @annotation.nowarn("cat=deprecation")
  private val derived: Map[String, Seq[String]] = Map(
    "adaptor" -> KnownOptions.adaptor,
    "application" -> KnownOptions.application,
    "connector" -> KnownOptions.connector,
    "context" -> KnownOptions.context,
    "domain" -> KnownOptions.domain,
    "entity" -> KnownOptions.entity,
    "epic" -> KnownOptions.epic,
    "projector" -> KnownOptions.projector,
    "repository" -> KnownOptions.repository,
    "saga" -> KnownOptions.saga,
    "streamlet" -> KnownOptions.streamlet,
    "portlet" -> KnownOptions.portlet
  )

  "KnownOptions" should {
    "not shrink: every derived list still contains all its former hand-written names" in {
      baseline.keys.toSeq.sorted.foreach { listName =>
        val expected = baseline(listName)
        val actual = derived.getOrElse(
          listName,
          fail(s"KnownOptions.$listName no longer exists")
        )
        val missing = expected.filterNot(actual.contains)
        withClue(s"KnownOptions.$listName lost option(s) ${missing.mkString(", ")}: ") {
          missing mustBe empty
        }
      }
    }

    "cover exactly the twelve documented definition kinds" in {
      derived.keySet mustBe baseline.keySet
    }

    "be derived, i.e. each list is sorted and free of duplicates" in {
      derived.foreach { case (listName, options) =>
        withClue(s"KnownOptions.$listName: ") {
          options mustBe options.distinct
          options mustBe options.sorted
        }
      }
    }
  }

  "RecognizedOptions" should {
    "put a universal (empty validParents) option in every derived list" in {
      RecognizedOptions.registry("technology").validParents mustBe empty
      derived.foreach { case (listName, options) =>
        withClue(s"KnownOptions.$listName is missing the universal 'technology': ") {
          options must contain("technology")
        }
      }
    }

    "list all universal options in universalOptions" in {
      val expected =
        RecognizedOptions.registry.filter(_._2.validParents.isEmpty).keys.toSeq.sorted
      RecognizedOptions.universalOptions mustBe expected
      // Every universal option is offered for any kind at all, even an invented one
      RecognizedOptions.optionsFor("NoSuchKind") mustBe expected
    }

    "accept 'transient' on both an Entity and a Repository" in {
      val spec = RecognizedOptions.registry("transient")
      spec.validParents must contain("Entity")
      spec.validParents must contain("Repository")
      RecognizedOptions.optionsFor("Entity") must contain("transient")
      RecognizedOptions.optionsFor("Repository") must contain("transient")
    }

    "register the six formerly-missing entity options" in {
      val six =
        Seq("transient", "event-sourced", "value", "consistent", "available", "message-queue")
      six.foreach { name =>
        withClue(s"option '$name' is not registered: ") {
          RecognizedOptions.registry.contains(name) mustBe true
        }
        withClue(s"option '$name' is not offered for an Entity: ") {
          RecognizedOptions.optionsFor("Entity") must contain(name)
        }
        // All six are simple markers taking no arguments
        RecognizedOptions.registry(name).minArgs mustBe 0
        RecognizedOptions.registry(name).maxArgs mustBe 0
      }
    }

    "offer 'protocol' on every streamlet SHAPE, never on the literal kind 'Streamlet'" in {
      // A Streamlet's Definition.kind is its shape's simple name, so a validParents entry of
      // "Streamlet" could never match. This was the original drift bug.
      RecognizedOptions.registry("protocol").validParents must not contain "Streamlet"
      RecognizedOptions.streamletKinds.foreach { shape =>
        withClue(s"'protocol' is not offered on a $shape streamlet: ") {
          RecognizedOptions.optionsFor(shape) must contain("protocol")
        }
      }
      RecognizedOptions.optionsFor("Streamlet") must not contain "protocol"
    }

    "offer 'async' on both portlet kinds" in {
      RecognizedOptions.portletKinds.foreach { kind =>
        RecognizedOptions.optionsFor(kind) must contain("async")
      }
    }

    "never name an option that could not be parsed" in {
      RecognizedOptions.registry.keys.foreach { name =>
        withClue(s"option name '$name' is not a legal identifier: ") {
          name.nonEmpty mustBe true
          name.forall(c => c.isLetterOrDigit || c == '-' || c == '_') mustBe true
        }
      }
    }
  }
}
