/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.stats.{StatsOutput, StatsPass}
import com.ossuminc.riddl.passes.{Pass, PassInput}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}

import org.scalatest.*
import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Tests for [[RootComparison]] — the deterministic, model-free `Root`
  * similarity API used by ossum-gen's compositional-fidelity benchmark.
  */
class RootComparisonTest extends AbstractValidatingTest {

  /** Parse-only (no validation) — comparison operates on any parseable Root. */
  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Load and parse a real in-repo `.riddl` model from the working dir. */
  private def parseFile(rel: String, td: TestData): Root =
    val url = PathUtils.urlFromCwdPath(Path.of(rel))
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      TopLevelParser.parseInput(rpi) match
        case Right(root) => root
        case Left(msgs)  => fail(s"parse of $rel failed:\n${msgs.format}")
    }
    Await.result(future, 10.seconds)

  private def statsOf(root: Root): StatsOutput =
    Pass
      .runThesePasses(PassInput(root), Pass.informationPasses)
      .outputs
      .outputOf[StatsOutput](StatsPass.name)
      .getOrElse(fail("StatsPass produced no output"))

  // A coherent base model: 1 domain, 2 contexts, 3 entities, 2 commands,
  // 1 event, 1 type, 3 handlers.
  private val base =
    """domain Shopping is {
      |  context Ordering is {
      |    command PlaceOrder is { sku: String }
      |    event OrderPlaced is { orderRef: String }
      |    type Sku is String
      |    entity Cart is { handler Main is { ??? } }
      |    entity Order is { handler Main is { ??? } }
      |  }
      |  context Billing is {
      |    command ChargeCard is { total: String }
      |    entity Invoice is { handler Main is { ??? } }
      |  }
      |}
      |""".stripMargin

  // Same structure as base, every name changed (renamed-but-equal).
  private val renamed =
    """domain Retail is {
      |  context Fulfillment is {
      |    command SubmitOrder is { code: String }
      |    event OrderSubmitted is { ref: String }
      |    type ProductCode is String
      |    entity Basket is { handler Primary is { ??? } }
      |    entity Purchase is { handler Primary is { ??? } }
      |  }
      |  context Payments is {
      |    command DebitAccount is { amount: String }
      |    entity Receipt is { handler Primary is { ??? } }
      |  }
      |}
      |""".stripMargin

  // Same as base but names differ only in case, with shifted source
  // locations (extra blank lines / indentation).
  private val caseLoc =
    """domain SHOPPING is {
      |
      |
      |  context ordering is {
      |      command placeorder is { sku: String }
      |      event ORDERPLACED is { orderRef: String }
      |      type sku is String
      |      entity CART is { handler main is { ??? } }
      |      entity ORDER is { handler MAIN is { ??? } }
      |  }
      |  context BILLING is {
      |      command chargecard is { total: String }
      |      entity invoice is { handler Main is { ??? } }
      |  }
      |}
      |""".stripMargin

  // A structurally and semantically unrelated model.
  private val unrelated =
    """domain Weather is {
      |  context Forecasting is {
      |    query GetForecast is { region: String }
      |    result Forecast is { temp: String }
      |    projector Daily is { ??? }
      |  }
      |}
      |""".stripMargin

  "RootComparison" should {

    "score identical roots at exactly 1.0" in { (td: TestData) =>
      val root = parse(base, "base")
      RootComparison.compareRoots(root, root).score mustBe 1.0
    }

    "score two empty roots at 1.0" in { (td: TestData) =>
      RootComparison.compareRoots(Root.empty, Root.empty).score mustBe 1.0
    }

    "report per-kind counts that match a manual StatsPass reading" in {
      (td: TestData) =>
        val root = parse(base, "base")
        val sim = RootComparison.compareRoots(root, root)
        val stats = statsOf(root)
        // For every branch kind StatsPass tallies (excluding the "All"
        // totals and structural wrappers with no id), the comparison's
        // count must equal the StatsPass count.
        for (kind, ks) <- stats.categories if kind != "All" do
          if sim.counts.contains(kind) then
            withClue(s"kind=$kind: ") {
              sim.counts(kind)._1.toLong mustBe ks.count
            }
        // Spot-check the DDD-structural kinds explicitly.
        sim.counts("Domain")._1 mustBe 1
        sim.counts("Context")._1 mustBe 2
        sim.counts("Entity")._1 mustBe 3
        sim.counts("Command")._1 mustBe 2
        sim.counts("Event")._1 mustBe 1
        sim.counts("Type")._1 mustBe 1
        sim.counts("Handler")._1 mustBe 3
    }

    "count breadth as top-level containers, not the Root wrapper" in {
      (td: TestData) =>
        val twoDomains = parse(
          """domain One is { context A is { ??? } }
            |domain Two is { context B is { ??? } }
            |""".stripMargin,
          "two"
        )
        val sim = RootComparison.compareRoots(twoDomains, twoDomains)
        sim.breadthA mustBe 2
        // The Root pseudo-kind must not appear in the comparison.
        sim.counts.keySet must not contain "Root"
    }

    "report depth matching StatsPass.maximum_depth" in { (td: TestData) =>
      val root = parse(base, "base")
      val sim = RootComparison.compareRoots(root, root)
      sim.depthA mustBe statsOf(root).maximum_depth
      sim.depthB mustBe statsOf(root).maximum_depth
    }

    "match names location- and case-independently" in { (td: TestData) =>
      val a = parse(base, "base")
      val b = parse(caseLoc, "caseLoc")
      val sim = RootComparison.compareRoots(a, b)
      // Case + location variations must not lower the score.
      sim.score must be >= 0.99
      // Every entity in A has a fuzzy counterpart in B (nothing unmatched).
      val entity = sim.perKind.find(_.kind == "Entity").get
      entity.matched.size mustBe 3
      entity.unmatchedA mustBe empty
      entity.unmatchedB mustBe empty
    }

    "keep a renamed-but-structurally-equal model high on structure" in {
      (td: TestData) =>
        val a = parse(base, "base")
        val b = parse(renamed, "renamed")
        val sim = RootComparison.compareRoots(a, b)
        // Counts line up (structure preserved) ...
        sim.counts("Entity") mustBe (3, 3)
        sim.counts("Context") mustBe (2, 2)
        // ... so the score stays well above the unrelated floor, but below
        // an identical/case-only match because the names all differ.
        sim.score must be > 0.5
        sim.score must be < 1.0
    }

    "order identical > case-only > renamed > unrelated" in { (td: TestData) =>
      val a = parse(base, "base")
      val identical = RootComparison.compareRoots(a, a).score
      val caseOnly = RootComparison.compareRoots(a, parse(caseLoc, "c")).score
      val ren = RootComparison.compareRoots(a, parse(renamed, "r")).score
      val unrel = RootComparison.compareRoots(a, parse(unrelated, "u")).score
      identical mustBe 1.0
      (identical >= caseOnly) mustBe true
      (caseOnly > ren) mustBe true
      (ren > unrel) mustBe true
    }

    "render a Markdown report with counts, names, metrics, and score" in {
      (td: TestData) =>
        val a = parse(base, "base")
        val b = parse(renamed, "renamed")
        val md = RootComparison.similarityMarkdown(a, b)
        md must include("# Root Similarity Report")
        md must include("Overall similarity score")
        md must include("## Structural metrics")
        md must include("## Per-kind counts")
        md must include("| Kind | A | B | Matched | Score |")
        md must include("## Name matching")
        md must include("Entity")
        md must include("Max depth")
    }

    "work on real, non-trivial in-repo models (self = 1.0 > cross)" in {
      (td: TestData) =>
        val dokn = parseFile("language/input/dokn.riddl", td)
        val rbbq = parseFile("language/input/rbbq.riddl", td)
        val self = RootComparison.compareRoots(dokn, dokn).score
        val cross = RootComparison.compareRoots(dokn, rbbq).score
        self mustBe 1.0
        (self > cross) mustBe true
    }
  }
}
