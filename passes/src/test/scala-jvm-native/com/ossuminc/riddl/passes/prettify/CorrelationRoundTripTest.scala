/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A70 — a [[Correlation]] must survive parse -> prettify -> parse unchanged.
  *
  * This exists because of a specific near-miss: A57's `on other as x` bound its envelope in
  * `format` but not in the emitter's declaration path, so prettify DROPPED the binding on every
  * round trip while every other test stayed green. A Correlation is more exposed to that failure
  * than most nodes, because `timeout` and `timeoutStatements` live in FIELDS rather than in
  * `contents` — generic traversal never reaches them, so only `closeCorrelation` emitting them
  * explicitly keeps them alive.
  *
  * Key ORDER is asserted, not just key membership: §6.5 makes identity the full tuple and forbids
  * canonicalizing, so a prettifier that sorted the keys would silently change what the model says.
  */
class CorrelationRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src: String =
    """domain D is {
      |  context C is {
      |    command RecordFulfillment is { customerId: String, orderId: String, paidAmount: Number }
      |    event PaymentTaken is { amount: Number }
      |    command ReportStalled is { why: String }
      |    entity Monitor is {
      |      handler H is { on command ReportStalled is { do "record it" } }
      |    }
      |    repository Store is { ??? }
      |    projector FulfillmentView is {
      |      updates repository Store
      |      correlation FulfillmentJoin by customerId, orderId yields command RecordFulfillment is {
      |        handler Collect is {
      |          on e: event PaymentTaken is { set field paidAmount to e.amount }
      |        }
      |      } times out after "30 days" {
      |        tell command ReportStalled to entity Monitor
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def onlyCorrelation(root: Root): Correlation =
    Finder(root).recursiveFindByType[Correlation] match
      case Seq(one) => one
      case other    => fail(s"expected exactly one Correlation, found ${other.size}")

  "Correlation round-trip" should {

    "emit the whole declaration" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("correlation FulfillmentJoin by customerId, orderId")
      pretty must include("yields command RecordFulfillment")
      // The clause that a `format`-only implementation would have dropped.
      pretty must include("""times out after "30 days"""")
      pretty must include("tell command ReportStalled to entity Monitor")
    }

    "survive the round trip with keys in the order written" in { (td: TestData) =>
      val before = onlyCorrelation(parse(src, "src"))
      before.keys.map(_.value) mustBe Seq("customerId", "orderId")

      val after = onlyCorrelation(parse(prettify(parse(src, "src")), "regen"))
      after.id.value mustBe before.id.value
      after.keys.map(_.value) mustBe Seq("customerId", "orderId")
      after.yields.pathId.value mustBe before.yields.pathId.value
      after.timeout.s mustBe "30 days"
      after.timeoutStatements.toSeq.size mustBe before.timeoutStatements.toSeq.size
      after.handlers.size mustBe before.handlers.size
    }

    "keep the correlation inside its projector, not hoisted" in { (td: TestData) =>
      // A definition that round-trips in ISOLATION but lands somewhere else is still broken.
      val root = parse(prettify(parse(src, "src")), "regen")
      val projector = Finder(root).recursiveFindByType[Projector].head
      projector.correlations.map(_.id.value) mustBe Seq("FulfillmentJoin")
    }
  }
}
