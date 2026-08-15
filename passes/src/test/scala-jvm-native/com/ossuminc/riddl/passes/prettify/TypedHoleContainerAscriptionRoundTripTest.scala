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

/** A20 whole-branch review, residual finding (2026-08-15): `TypedHoleAscriptionShapesRoundTripTest`
  * proved the four previously-broken ascription shapes (`as any of {…}`, `as table of T of […]`,
  * `as reference to entity E`, `as Currency(USD)`) round-trip correctly at the FOUR positions
  * `emitValue` was wired at (`constant`, `let`, `set`, `when`). It did not prove the same for a
  * `PromptValue` nested INSIDE a container -- a `Constructor`/`Call` argument, an `InvariantCondition`
  * `with` argument reached through a `LogicalExpression`/`NotExpression` -- because `emitValue`'s
  * fallback for every OTHER `Value` shape was still `add(other.format)`, which recurses back into
  * the un-fixed `.format`/`ascriptionFormat` path one level down. `emitValue` is now total over
  * these containers too (`Constructor`, `Call`, `Initiate`, `InvariantCondition`,
  * `LogicalExpression`, `NotExpression` all recurse back through `emitValue`), and this file proves
  * it the same way: parse -> prettify -> re-parse, asserting the SAME broken-shape ascription
  * survives at the same place.
  *
  * Also covers the two structural asks from the review: a NAMED constructor/call argument (so the
  * `<name> = ` prefix is proven to survive alongside the routed value), and a nested
  * `LogicalExpression` operand (so the parenthesizing `LogicalExpression.format`'s `paren` helper
  * applies is proven to survive through `emitLogicalOperand`, not merely the ascription inside it).
  */
class TypedHoleContainerAscriptionRoundTripTest extends AbstractValidatingTest {

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

  private def letExpression(root: Root, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let statement named '$name' found"))
      .expression

  // One model exercising, in order: a NAMED Constructor argument ascribed to an Enumeration, a
  // NAMED Call argument ascribed to a Currency, a nested LogicalExpression whose left operand is a
  // parenthesized `and` holding an InvariantCondition ascribed to an EntityReferenceTypeExpression,
  // and a `not` wrapping an InvariantCondition ascribed to a Table.
  private def model: String =
    """domain D is {
      |  context C is {
      |    entity Target is { ??? }
      |    record Item is { color: String, active: Boolean }
      |    record PayArgs is { amt: String }
      |    record Sum is { total: Integer }
      |    function Pay is {
      |      requires record PayArgs
      |      returns record Sum
      |      return record Sum(total = "0")
      |    }
      |    entity E is {
      |      invariant HasFunds is "always true"
      |      handler H is {
      |        on init {
      |          let viaConstructor = record Item(
      |            color = prompt("pick a color") as any of { Red, Green },
      |            active = true
      |          )
      |          let viaCall = call function Pay(amt = prompt("compute amount") as Currency(USD))
      |          when (invariant HasFunds with prompt("target ref") as reference to entity Target and flag) or otherFlag then
      |            do "ok"
      |          end
      |          when not invariant HasFunds with prompt("check qty") as table of Integer of [3,3] then
      |            do "no"
      |          end
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "a typed hole nested inside a container" should {

    "survive a prettify round trip as a NAMED Constructor argument ascribed to an Enumeration" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        letExpression(original, "viaConstructor") match
          case c: Constructor =>
            c.args.find(_.name.exists(_.value == "color")).map(_.value) match
              case Some(pv: PromptValue) => pv.typeEx.get mustBe an[Enumeration]
              case other                 => fail(s"expected a PromptValue 'color' arg, got $other")
          case other => fail(s"expected a Constructor, got $other")

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""color = prompt("pick a color") as any of {""")
          // The un-broken sibling argument (a plain BooleanLiteral, no PromptValue at all) must
          // survive byte-identically alongside the fixed one -- proof `emitConstructorArgs` did not
          // regress the ordinary case while fixing the broken one.
          emitted must include("active = true")
        }

        val regen = parse(emitted, "regen")
        letExpression(regen, "viaConstructor") match
          case c: Constructor =>
            c.args.find(_.name.exists(_.value == "color")).map(_.value) match
              case Some(pv: PromptValue) =>
                pv.typeEx.get match
                  case e: Enumeration => e.enumerators.toSeq.map(_.id.value) mustBe Seq("Red", "Green")
                  case other          => fail(s"expected an Enumeration, got $other")
              case other => fail(s"expected a PromptValue 'color' arg, got $other")
            c.args.find(_.name.exists(_.value == "active")).map(_.value) match
              case Some(BooleanLiteral(_, b)) => b mustBe true
              case other                      => fail(s"expected a BooleanLiteral 'active' arg, got $other")
          case other => fail(s"expected a Constructor, got $other")
    }

    "survive a prettify round trip as a NAMED Call argument ascribed to a Currency" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        letExpression(original, "viaCall") match
          case c: Call =>
            c.args.find(_.name.exists(_.value == "amt")).map(_.value) match
              case Some(pv: PromptValue) => pv.typeEx.get mustBe a[Currency]
              case other                 => fail(s"expected a PromptValue 'amt' arg, got $other")
          case other => fail(s"expected a Call, got $other")

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""amt = prompt("compute amount") as Currency(USD)""")
          // Before the fix this rendered `as Currency` -- a parse error, since `Currency` requires
          // a mandatory `country` argument. Guard against regressing to that specific breakage.
          emitted must not include """as Currency)"""
          emitted must not include """as Currency """
        }

        val regen = parse(emitted, "regen")
        letExpression(regen, "viaCall") match
          case c: Call =>
            c.args.find(_.name.exists(_.value == "amt")).map(_.value) match
              case Some(pv: PromptValue) =>
                pv.typeEx.get match
                  case cur: Currency => cur.country mustBe "USD"
                  case other         => fail(s"expected a Currency, got $other")
              case other => fail(s"expected a PromptValue 'amt' arg, got $other")
          case other => fail(s"expected a Call, got $other")
    }

    "survive a prettify round trip through a nested LogicalExpression, preserving the " +
      "parenthesized grouping, with an ascription to an EntityReferenceTypeExpression" in {
        (_: TestData) =>
          val original = parse(model, "orig")
          val ws1 = Finder(original)
            .recursiveFindByType[WhenStatement]
            .find(_.condition.isInstanceOf[LogicalExpression])
            .getOrElse(fail("no LogicalExpression when-condition found"))

          // `Any`, not `Value`: `WhenStatement.condition`'s declared type is
          // `LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue`, which is not
          // a subtype of `Value` (it includes `Identifier`, which `Value` does not), so a `Value`
          // parameter would reject it at the call site. The match below only cares about the
          // runtime case-class shape.
          def refPromptOf(v: Any): PromptValue = v match
            case LogicalExpression(_, LogicalOperator.Or, left, _) =>
              left match
                case LogicalExpression(_, LogicalOperator.And, ic: InvariantCondition, _) =>
                  ic.argument match
                    case Some(pv: PromptValue) => pv
                    case other                 => fail(s"expected a PromptValue IC argument, got $other")
                case other => fail(s"expected And on the left of Or, got $other")
            case other => fail(s"expected Or at the root, got $other")

          refPromptOf(ws1.condition).typeEx.get mustBe an[EntityReferenceTypeExpression]

          val emitted = prettify(original)
          withClue(s"emitted source was:\n$emitted\n") {
            // The nested `and` must stay parenthesized under the outer `or` -- the same rule
            // `LogicalExpression.format`'s `paren` helper enforces, now mirrored by
            // `emitLogicalOperand`.
            emitted must include(
              """when (invariant HasFunds with prompt("target ref") as reference to entity""" +
                """ Target and flag) or otherFlag then"""
            )
          }

          val regen = parse(emitted, "regen")
          val ws2 = Finder(regen)
            .recursiveFindByType[WhenStatement]
            .find(_.condition.isInstanceOf[LogicalExpression])
            .getOrElse(fail("no LogicalExpression when-condition found after re-parse"))
          val pv2 = refPromptOf(ws2.condition)
          pv2.typeEx.get match
            case er: EntityReferenceTypeExpression => er.entity.value.last mustBe "Target"
            case other                              => fail(s"expected an EntityReferenceTypeExpression, got $other")
      }

    "survive a prettify round trip through a `not`, with an ascription to a Table" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        val ws1 = Finder(original)
          .recursiveFindByType[WhenStatement]
          .find(_.condition.isInstanceOf[NotExpression])
          .getOrElse(fail("no NotExpression when-condition found"))

        def tablePromptOf(v: Any): PromptValue = v match
          case NotExpression(_, ic: InvariantCondition) =>
            ic.argument match
              case Some(pv: PromptValue) => pv
              case other                 => fail(s"expected a PromptValue IC argument, got $other")
          case other => fail(s"expected Not at the root, got $other")

        tablePromptOf(ws1.condition).typeEx.get mustBe a[Table]

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include(
            """when not invariant HasFunds with prompt("check qty") as table of Integer""" +
              """ of [ 3, 3 ] then"""
          )
          // Before the fix this rendered `as table of Integer(3,3)` -- a parse error, since
          // `tableType` requires the second `of`.
          emitted must not include "Integer(3,3)"
        }

        val regen = parse(emitted, "regen")
        val ws2 = Finder(regen)
          .recursiveFindByType[WhenStatement]
          .find(_.condition.isInstanceOf[NotExpression])
          .getOrElse(fail("no NotExpression when-condition found after re-parse"))
        tablePromptOf(ws2.condition).typeEx.get match
          case t: Table => t.dimensions mustBe Seq(3L, 3L)
          case other    => fail(s"expected a Table, got $other")
    }

    // 2026-08-15 review follow-up: `InvariantBlock`'s `predicate` (its OWN final boolean, not one
    // of its leading `statements`) is `BooleanExpression`, exactly the shape `emitValue` is total
    // over elsewhere, and it was still rendered via `AST.InvariantBlock.format` (-> `.format` on
    // the predicate) in `PrettifyVisitor.doInvariant`. `statements` are a separate, still-open
    // residual (layout-entangled, not fixed here -- see `RiddlFileEmitter.emitInvariantBlock`'s
    // doc): this case has ZERO leading statements, so it isolates the predicate fix specifically.
    "survive a prettify round trip as an InvariantBlock's own predicate, ascribed to a Currency" in {
      (_: TestData) =>
        val src =
          """domain D is {
            |  context C is {
            |    entity E is {
            |      invariant OtherRule is "always true"
            |      invariant HasFunds is {
            |        invariant OtherRule with prompt("w") as Currency(USD)
            |      }
            |    }
            |  }
            |}
            |""".stripMargin

        def predicateOf(root: Root): InvariantCondition =
          Finder(root)
            .recursiveFindByType[Invariant]
            .find(_.id.value == "HasFunds")
            .getOrElse(fail("no invariant named 'HasFunds' found"))
            .condition match
              case Some(InvariantBlock(_, _, ic: InvariantCondition)) => ic
              case other => fail(s"expected an InvariantBlock with an InvariantCondition predicate, got $other")

        val original = parse(src, "orig")
        predicateOf(original).argument match
          case Some(pv: PromptValue) => pv.typeEx.get mustBe a[Currency]
          case other                 => fail(s"expected a PromptValue argument, got $other")

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include(
            """invariant HasFunds is { invariant OtherRule with prompt("w") as Currency(USD) }"""
          )
          // Before the fix this rendered `as Currency` -- a parse error (mandatory `country` arg).
          emitted must not include """as Currency }"""
        }

        val regen = parse(emitted, "regen")
        predicateOf(regen).argument match
          case Some(pv: PromptValue) =>
            pv.typeEx.get match
              case c: Currency => c.country mustBe "USD"
              case other       => fail(s"expected a Currency, got $other")
          case other => fail(s"expected a PromptValue argument, got $other")
    }
  }
}
