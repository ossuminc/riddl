/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Correlation, Module, Root}
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A70 — a [[Correlation]] must survive AST -> BAST -> AST.
  *
  * BAST is the second of RIDDL's serialization surfaces, and the one where a mistake is hardest to
  * read: `writeCorrelation` emits the header while the Pass interleaves count-then-items for
  * `contents` and then `timeoutStatements`, so the reader must consume them in exactly that order.
  * Getting it wrong misaligns every byte that follows and surfaces far away as "Invalid string
  * table index" rather than as a decode failure at the correlation.
  *
  * The two-key case is deliberate: a single-key correlation would still decode if the key sequence
  * were written as a bare identifier rather than a counted sequence.
  */
class CorrelationBASTRoundTripTest extends AnyWordSpec with Matchers {

  private val source: String =
    """domain D is {
      |  context C is {
      |    command Fulfillment is { customerId: String, orderId: String, paidAmount: Number }
      |    event PaymentTaken is { amount: Number }
      |    command ReportStalled is { why: String }
      |    entity Monitor is {
      |      handler H is { on command ReportStalled is { do "record it" } }
      |    }
      |    repository Store is { ??? }
      |    projector FulfillmentView is {
      |      updates repository Store
      |      correlation FulfillmentJoin by customerId, orderId yields command Fulfillment is {
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

  "Correlation BAST round trip" should {

    "preserve keys, target, timeout and both statement lists" in {
      val original: Root =
        TopLevelParser.parseInput(RiddlParserInput(source, "corr-bast"), true) match
          case Right(root) => root
          case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

      val writerResult = Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val bytes = writerResult
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes

      // BAST decodes to a Module (the nebula the writer wraps a Root in), not to a Root.
      val decoded = BASTReader(bytes).read() match
        case Right(module) => module
        case Left(msgs)    => fail(s"BAST decode failed:\n${msgs.format}")

      val before = Finder(original).recursiveFindByType[Correlation].head
      val after = Finder(decoded).recursiveFindByType[Correlation] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one Correlation after decode, got ${other.size}")

      after.id.value mustBe before.id.value
      // Order, not just membership: §6.5 makes identity the full tuple and forbids canonicalizing.
      after.keys.map(_.value) mustBe Seq("customerId", "orderId")
      after.yields.pathId.value mustBe before.yields.pathId.value
      after.timeout.s mustBe "30 days"
      after.handlers.size mustBe before.handlers.size
      // The list a misordered reader would silently lose or mangle.
      after.timeoutStatements.toSeq.size mustBe before.timeoutStatements.toSeq.size
      after.timeoutStatements.toSeq.size mustBe 1
    }
  }
}
