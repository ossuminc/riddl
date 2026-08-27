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

/** A54: operand widening — `send`/`tell`/`yield` (message ref | constructor), `morph` (record ref |
  * constructor), `set`/`let` (any value), plus the `prompt(...)` value. RIDDL is reflective, so
  * every widened form must emit (prettify) and re-parse to the same shape, in the same place.
  */
class WidenedValueRoundTripTest extends AbstractValidatingTest {

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
    """domain d is {
      |  context c is {
      |    type Qty is Integer
      |    record Line is { sku: String, qty: Qty }
      |    command Add is { sku: String }
      |    event Added is { sku: String }
      |    result Res is { ok: String }
      |    query Ask replies result Res is { q: String }
      |    outlet outp is event Added
      |    entity E is {
      |      record Data is { line: Line }
      |      state S of record Data
      |      handler H is {
      |        on command Add {
      |          let note = prompt("summarize the addition")
      |          set field E.S.line to record Line(sku = "x", qty = "1")
      |          send event Added(sku = "x") to outlet c.outp
      |          morph entity E to state E.S with record Data(line = record Line(sku = "y", qty = "2"))
      |        }
      |        on query Ask {
      |          reply result Res(ok = "done")
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "widened operands" should {
    "round-trip send/morph/set/let(prompt)/yield constructors through prettify" in {
      (td: TestData) =>
        val pretty = prettify(parse(src, "src"))
        // The `prompt(...)` value survives emission verbatim.
        pretty must include("prompt(\"summarize the addition\")")

        val regen = parse(pretty, "regen")

        // let bound to a PromptValue
        val lets = Finder(regen).recursiveFindByType[LetStatement]
        val promptLet = lets
          .find(_.identifier.value == "note")
          .getOrElse(fail("let 'note' lost through round-trip"))
        promptLet.expression match
          case pv: PromptValue => pv.text mustBe "summarize the addition"
          case other           => fail(s"expected a PromptValue expression, got $other")

        // set field to a record constructor
        val set = Finder(regen)
          .recursiveFindByType[SetStatement]
          .headOption
          .getOrElse(fail("set statement lost"))
        set.value match
          case c: Constructor =>
            c.ref.isInstanceOf[RecordRef] mustBe true
            c.args.map(_.name.map(_.value)) mustBe Seq(Some("sku"), Some("qty"))
          case other => fail(s"expected a Constructor set value, got $other")

        // send an event built by a constructor
        val send = Finder(regen)
          .recursiveFindByType[SendStatement]
          .headOption
          .getOrElse(fail("send statement lost"))
        send.msg match
          case c: Constructor =>
            c.ref.isInstanceOf[EventRef] mustBe true
            c.ref.pathId.value.last mustBe "Added"
          case other => fail(s"expected a Constructor send msg, got $other")

        // morph the entity state with a (nested) record constructor
        val morph = Finder(regen)
          .recursiveFindByType[MorphStatement]
          .headOption
          .getOrElse(fail("morph statement lost"))
        morph.value match
          case c: Constructor =>
            c.ref.isInstanceOf[RecordRef] mustBe true
            c.args.head.value.isInstanceOf[Constructor] mustBe true // nested Line(...)
          case other => fail(s"expected a Constructor morph value, got $other")

        // REPLY a result built by a constructor -- `reply`, not `yield`, since 2.0 gives a
        // query's result its own statement.
        val yld = Finder(regen)
          .recursiveFindByType[ReplyStatement]
          .headOption
          .getOrElse(fail("reply statement lost"))
        yld.msg match
          case c: Constructor =>
            c.ref.isInstanceOf[ResultRef] mustBe true
          case other => fail(s"expected a Constructor yield msg, got $other")
    }
  }
}
