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

/** Message-value-source Task 3, the prettify half: `yield`, `reply` and `morph … with` now accept a
  * bare [[ValueRef]] operand (Task 2), so RIDDL's reflectivity mandate requires that operand to
  * survive parse → prettify → re-parse **as a ValueRef** and not be rewritten into a keyword-led
  * ref.
  *
  * The counterpart for `send`/`tell` is `BoundMessageOperandRoundTripTest`; the BAST and JSON
  * halves are `BASTRoundTripTest` and the JSON coverage ledger.
  *
  * Asserting the NODE KIND, not just the rendered text, is the point. `yield evt` and `yield event
  * evt` render differently but a test that only compared strings would pass on output that had
  * silently changed which AST node the model contains.
  */
class ValueOperandRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain d is {
      |  context c is {
      |    command Foo yields event Bar is { a: Integer }
      |    event Bar is { b: Integer }
      |    query Qry replies result Res is { q: Integer }
      |    result Res is { r: Integer }
      |    record Data is { evt: d.c.Bar, answer: d.c.Res, n: Integer }
      |    record Other is { m: Integer }
      |    record Holder is { next: d.c.Other }
      |    entity src is {
      |      state S of record d.c.Data
      |      state T of record d.c.Other
      |      state H of record d.c.Holder
      |      handler Ops is {
      |        on command d.c.Foo is {
      |          morph entity d.c.src to state T with next
      |          yield evt
      |        }
      |        on query d.c.Qry is { reply answer }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  /** (statement-kind, operand-rendering, is-it-a-ValueRef) in source order. */
  private def operands(root: Root): Seq[(String, String, Boolean)] =
    Finder(root).recursiveFindByType[Statement].collect {
      case y: YieldStatement => ("yield", y.msg.format, y.msg.isInstanceOf[ValueRef])
      case r: ReplyStatement => ("reply", r.msg.format, r.msg.isInstanceOf[ValueRef])
      case m: MorphStatement => ("morph", m.value.format, m.value.isInstanceOf[ValueRef])
    }

  "a value operand on yield/reply/morph" should {

    "be parsed as a ValueRef, not a keyword-led ref" in { (_: TestData) =>
      val found = operands(parse(src, "valueOperands"))
      found.map(t => t._1 -> t._2) mustBe Seq(
        "morph" -> "next",
        "yield" -> "evt",
        "reply" -> "answer"
      )
      found.map(_._3) mustBe Seq(true, true, true)
    }

    "survive a prettify round trip unchanged" in { (_: TestData) =>
      val pretty = prettify(parse(src, "valueOperands"))
      withClue(s"prettified output was:\n$pretty") {
        val again = operands(parse(pretty, "regen"))
        again mustBe operands(parse(src, "valueOperands"))
        again.map(_._3) mustBe Seq(true, true, true)
      }
    }

    "not acquire a message-kind keyword in the emitted source" in { (_: TestData) =>
      val pretty = prettify(parse(src, "valueOperands"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("yield evt")
        pretty must include("reply answer")
        pretty must include("with next")
        // The failure this guards: re-emitting the operand through a MessageRef would produce
        // `yield event evt`, which re-parses to a DIFFERENT node naming a type that does not exist.
        pretty mustNot include("yield event evt")
        pretty mustNot include("reply result answer")
      }
    }
  }
}
