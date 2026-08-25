/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A25: RIDDL is reflective — a `foreach` statement must emit (prettify) and re-parse to the same
  * shape (same element, same collection, same nested body) whether the collection is a field ref or
  * a `let`-bound local.
  */
class ForeachRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain d is { context c is {
      |  record Order is { id: Integer }
      |  type OrderList is many Order
      |  command Batch is { orders: OrderList }
      |  handler h is {
      |    on command Batch {
      |      let batch: OrderList = "orders"
      |      foreach o in field Batch.orders {
      |        foreach p in batch {
      |          do "process"
      |        }
      |      }
      |    }
      |  }
      |}}
      |""".stripMargin

  "foreach statement" should {
    "round-trip a field-ref and a local foreach through prettify" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("foreach o in field Batch.orders")
      pretty must include("foreach p in batch")

      val foreaches = Finder(parse(pretty, "regen")).recursiveFindByType[ForeachStatement]
      // The outer field-ref foreach and the inner local foreach both survive.
      val outer = foreaches.find(_.element.value == "o").getOrElse(fail("outer foreach lost"))
      outer.collection match
        case fr: FieldRef => fr.pathId.value mustBe Seq("Batch", "orders")
        case other        => fail(s"expected FieldRef collection, got $other")

      val inner = foreaches.find(_.element.value == "p").getOrElse(fail("inner foreach lost"))
      inner.collection match
        case id: Identifier => id.value mustBe "batch"
        case other          => fail(s"expected Identifier collection, got $other")
      // The nested prompt survives inside the inner foreach body.
      Finder(inner.doStatements).recursiveFindByType[DoStatement].size mustBe 1
    }
  }
}
