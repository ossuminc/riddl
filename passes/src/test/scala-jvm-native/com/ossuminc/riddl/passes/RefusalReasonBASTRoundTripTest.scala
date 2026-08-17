/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest

import org.scalatest.TestData

/** A38 (FORMAT_REVISION 18, 2026-08-17): a refusal step's reason may name the invariant the request
  * violates, so `writeRefusalInteraction` now writes a DISCRIMINATOR byte where a bare literal
  * string used to begin.
  *
  * Modeled on `BangNotBASTRoundTripTest` and `ConstantAndMethodBASTRoundTripTest`, for the reason
  * they both record: **a BAST error names where the reader DERAILED, never what derailed it.** A
  * discriminator the reader does not expect shifts every following byte by one, and the symptom
  * surfaces somewhere else entirely — as an invalid string-table index, or as a nonsense value in
  * an innocent node. So the case that actually proves writer and reader stayed in lockstep is the
  * one with definitions AFTER the affected node.
  *
  * The invariant path is written INLINE (no `NODE_PATH_IDENTIFIER` tag), matching
  * `require invariant X`, whose own comment records that using the tagged form there shifted every
  * subsequent byte by one and went unnoticed until something round-tripped it.
  */
class RefusalReasonBASTRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def roundTrip(src: String, origin: String): Module =
    val root = parse(src, origin)
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    BASTReader(bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")

  /** Both models carry a `AfterType` sibling and a second interaction AFTER the refusal — the
    * innocent bystanders that a one-byte misalignment would take down with it.
    */
  private def model(reason: String): String =
    s"""domain D is {
       |  context C is {
       |    entity Order is {
       |      invariant MustBePaid is "the order is paid for"
       |      handler H is { on other is { ??? } } with { briefly "h" }
       |    } with { briefly "e" }
       |    type AfterType is String with { briefly "after" }
       |  } with { briefly "c" }
       |  user Buyer is "a person"
       |  epic Buying is {
       |    user D.Buyer wants "to buy" so that "goods arrive"
       |    case primary is {
       |      user D.Buyer wants "to pay" so that "the order ships"
       |      step entity D.C.Order refuses user D.Buyer $reason
       |      step from user D.Buyer "confirms with" to entity D.C.Order
       |    }
       |  } with { briefly "ep" }
       |} with { briefly "d" }
       |""".stripMargin

  private def refusalIn(m: Module): RefusalInteraction =
    Finder(m)
      .recursiveFindByType[RefusalInteraction]
      .headOption
      .getOrElse(fail("no RefusalInteraction survived the round trip"))

  private def assertBystandersSurvived(m: Module): Unit =
    Finder(m)
      .recursiveFindByType[Type]
      .find(_.id.value == "AfterType")
      .getOrElse(fail("the sibling `AfterType` definition did not survive"))
      .typEx mustBe a[String_]
    Finder(m).recursiveFindByType[Interaction].size must be >= 2

  "a refusal reason" should {

    "round-trip the PROSE form, and not disturb what follows it" in { (td: TestData) =>
      val decoded = roundTrip(model(""""not authorized""""), s"prose-${td.name}")
      val r = refusalIn(decoded)
      r.reason mustBe a[LiteralString]
      r.reason.asInstanceOf[LiteralString].s mustBe "not authorized"
      assertBystandersSurvived(decoded)
    }

    "round-trip the INVARIANT form, and not disturb what follows it" in { (td: TestData) =>
      val decoded = roundTrip(model("invariant D.C.Order.MustBePaid"), s"inv-${td.name}")
      val r = refusalIn(decoded)
      r.reason mustBe a[InvariantRef]
      r.reason.asInstanceOf[InvariantRef].pathId.value mustBe Seq("D", "C", "Order", "MustBePaid")
      assertBystandersSurvived(decoded)
    }

    /* The discriminator's whole job: prose that spells a path must not come back as a reference. */
    "keep the two forms distinct even when the prose LOOKS like a path" in { (td: TestData) =>
      val decoded = roundTrip(model(""""D.C.Order.MustBePaid""""), s"lookalike-${td.name}")
      refusalIn(decoded).reason mustBe a[LiteralString]
    }
  }
}
