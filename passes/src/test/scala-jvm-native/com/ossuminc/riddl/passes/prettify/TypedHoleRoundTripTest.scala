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

/** A20: `prompt("…") as <type>` ascribes a type to an AI-computed value (a "typed hole"). Task 1
  * added `PromptValue.typeEx: Option[TypeExpression]` and taught `format` to append ` as <type>`
  * only when it is `Some`. Prettify emits every value polymorphically via `.format`
  * (`RiddlFileEmitter.emitStatement`/`emitConstant` call `expr.format`/`value.format` directly, or
  * — for a `when` condition — match `PromptValue` and call `pv.format`), so Task 1's `format` is
  * already the whole round-trip implementation. This test PROVES that, rather than assuming it.
  *
  * The thing worth getting right: a `format` that always appended ` as …` would round-trip every
  * ASCRIBED `prompt(...)` perfectly while silently corrupting every UNASCRIBED one, and a suite
  * that only covered the typed case would pass anyway. So every position below is exercised both
  * unascribed (`typeEx` must stay `None`) and ascribed (`typeEx` must survive as the SAME type
  * expression, not merely "some type"), in each of the four positions an ascription can occupy: a
  * `let`, a `constant`, a `when` condition, and a constructor argument.
  *
  * A separate case below (`optional`/`repeated`) covers a `Cardinality`-wrapped aliased ascription
  * (`as OrderId?`, `as OrderId*`). `TypeParser.cardinality` wraps ANY type alternative in
  * `Optional`/`ZeroOrMore`/`OneOrMore`/`SpecificRange`, including an aliased one, so this parses
  * today; `PromptValue.ascriptionFormat` must recurse through those wrappers the same way
  * `RiddlFileEmitter.emitTypeExpression` does, or the keyword bug this file exists to prevent
  * resurfaces one level down (`as type OrderId?` instead of `as OrderId?`). Found by code review
  * 2026-08-15.
  */
class TypedHoleRoundTripTest extends AbstractValidatingTest {

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

  // One model exercising an ascribed AND an unascribed `prompt(...)` in each of the four
  // positions: a `let`, a `constant`, a constructor argument (via `set field ... to record ...`),
  // and a `when` condition -- plus two cardinality-wrapped `let`s (`OrderId?`, `OrderId*`).
  // Modeled on `TypedHoleTest` (language/.../parsing), the parser-level suite Task 1 added for
  // this same fixture shape.
  private def model: String =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    record Line is { sku: String, note: String }
      |    command Add is { sku: String }
      |    constant Plain: Real = prompt("x")
      |    constant Typed: Real = prompt("x") as Real
      |    entity E is {
      |      record Data is { line: Line, qty: Integer }
      |      state S of record Data
      |      handler H is {
      |        on command Add is {
      |          let plain = prompt("x")
      |          let typed = prompt("x") as Real
      |          let aliased = prompt("x") as OrderId
      |          let optional = prompt("x") as OrderId?
      |          let repeated = prompt("x") as OrderId*
      |          set field E.S.line to record Line(sku = prompt("x") as String, note = prompt("x"))
      |          when prompt("x") as Boolean then
      |            do "something"
      |          end
      |          when prompt("x") then
      |            do "something else"
      |          end
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def letExpression(root: Root, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let statement named '$name' found"))
      .expression

  private def constantOf(root: Root, name: String): Constant =
    Finder(root)
      .recursiveFindByType[Constant]
      .find(_.id.value == name)
      .getOrElse(fail(s"no constant named '$name' found"))

  private def constructorArgPromptValue(root: Root, argName: String): PromptValue =
    Finder(root)
      .recursiveFindByType[SetStatement]
      .find(_.field match {
        case fr: FieldRef => fr.pathId.value.last == "line"
        case _            => false
      })
      .getOrElse(fail("no `set ... line` statement found"))
      .value match
      case c: Constructor =>
        c.args.find(_.name.exists(_.value == argName)).map(_.value) match
          case Some(pv: PromptValue) => pv
          case other => fail(s"expected a PromptValue arg named '$argName', got $other")
      case other => fail(s"expected a Constructor set value, got $other")

  // Two `when` statements share this model; disambiguate by whether the condition is ascribed.
  private def whenConditionPromptValues(root: Root): Seq[PromptValue] =
    Finder(root)
      .recursiveFindByType[WhenStatement]
      .map(_.condition)
      .collect { case pv: PromptValue => pv }

  private def ascribedWhenCondition(root: Root): PromptValue =
    whenConditionPromptValues(root)
      .find(_.typeEx.isDefined)
      .getOrElse(fail("no ascribed when condition found"))

  private def unascribedWhenCondition(root: Root): PromptValue =
    whenConditionPromptValues(root)
      .find(_.typeEx.isEmpty)
      .getOrElse(fail("no unascribed when condition found"))

  "a typed hole (prompt(...) as <type>)" should {

    "survive a prettify round trip in a `let`, ascribed and unascribed" in { (_: TestData) =>
      val original = parse(model, "orig")
      // Presence: absent before prettify too, so the assertion is meaningful.
      letExpression(original, "plain") match
        case pv: PromptValue => pv.typeEx mustBe None
        case other           => fail(s"expected a PromptValue, got $other")
      letExpression(original, "typed") match
        case pv: PromptValue => pv.typeEx.get mustBe a[Real]
        case other           => fail(s"expected a PromptValue, got $other")
      letExpression(original, "aliased") match
        case pv: PromptValue =>
          pv.typeEx.get match
            case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
            case other => fail(s"expected an AliasedTypeExpression, got $other")
        case other => fail(s"expected a PromptValue, got $other")

      val emitted = prettify(original)
      withClue(s"emitted source was:\n$emitted\n") {
        emitted must include("""let plain = prompt("x")""")
        // The absence check: the unascribed form must NOT pick up a trailing ` as ...` from a
        // format bug that always appends the ascription.
        emitted must not include """let plain = prompt("x") as"""
        emitted must include("""let typed = prompt("x") as Real""")
        emitted must include("""let aliased = prompt("x") as OrderId""")
      }

      val regen = parse(emitted, "regen")
      letExpression(regen, "plain") match
        case pv: PromptValue => pv.typeEx mustBe None
        case other           => fail(s"expected a PromptValue, got $other")
      letExpression(regen, "typed") match
        case pv: PromptValue => pv.typeEx.get mustBe a[Real]
        case other           => fail(s"expected a PromptValue, got $other")
      letExpression(regen, "aliased") match
        case pv: PromptValue =>
          pv.typeEx.get match
            case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
            case other => fail(s"expected an AliasedTypeExpression, got $other")
        case other => fail(s"expected a PromptValue, got $other")
    }

    "survive a prettify round trip in a `constant`, ascribed and unascribed" in { (_: TestData) =>
      val original = parse(model, "orig")
      constantOf(original, "Plain").value match
        case pv: PromptValue => pv.typeEx mustBe None
        case other           => fail(s"expected a PromptValue, got $other")
      constantOf(original, "Typed").value match
        case pv: PromptValue => pv.typeEx.get mustBe a[Real]
        case other           => fail(s"expected a PromptValue, got $other")

      val emitted = prettify(original)
      withClue(s"emitted source was:\n$emitted\n") {
        emitted must include("""constant Plain: Real = prompt("x")""")
        emitted must not include """constant Plain: Real = prompt("x") as"""
        emitted must include("""constant Typed: Real = prompt("x") as Real""")
      }

      val regen = parse(emitted, "regen")
      constantOf(regen, "Plain").value match
        case pv: PromptValue => pv.typeEx mustBe None
        case other           => fail(s"expected a PromptValue, got $other")
      constantOf(regen, "Typed").value match
        case pv: PromptValue => pv.typeEx.get mustBe a[Real]
        case other           => fail(s"expected a PromptValue, got $other")
    }

    "survive a prettify round trip as a constructor argument, ascribed and unascribed" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        constructorArgPromptValue(original, "sku").typeEx.get mustBe a[String_]
        constructorArgPromptValue(original, "note").typeEx mustBe None

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""sku = prompt("x") as String""")
          // The absence check: the unascribed arg must not pick up a trailing ` as ...`.
          emitted must include("""note = prompt("x"))""")
        }

        val regen = parse(emitted, "regen")
        constructorArgPromptValue(regen, "sku").typeEx.get mustBe a[String_]
        constructorArgPromptValue(regen, "note").typeEx mustBe None
    }

    "survive a prettify round trip as a `when` condition, ascribed and unascribed" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        ascribedWhenCondition(original).typeEx.get mustBe a[Bool]
        unascribedWhenCondition(original).typeEx mustBe None

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""when prompt("x") as Boolean then""")
          // The absence check: this exact line has no ` as ...` before `then`, so a format bug
          // that always appends the ascription would make this substring disappear.
          emitted must include("""when prompt("x") then""")
        }

        val regen = parse(emitted, "regen")
        ascribedWhenCondition(regen).typeEx.get mustBe a[Bool]
        unascribedWhenCondition(regen).typeEx mustBe None
    }

    "survive a prettify round trip through a Cardinality wrapper on an aliased ascription" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        letExpression(original, "optional") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case Optional(_, ate: AliasedTypeExpression) => ate.pathId.value.last mustBe "OrderId"
              case other => fail(s"expected Optional(AliasedTypeExpression), got $other")
          case other => fail(s"expected a PromptValue, got $other")
        letExpression(original, "repeated") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case ZeroOrMore(_, ate: AliasedTypeExpression) =>
                ate.pathId.value.last mustBe "OrderId"
              case other => fail(s"expected ZeroOrMore(AliasedTypeExpression), got $other")
          case other => fail(s"expected a PromptValue, got $other")

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""let optional = prompt("x") as OrderId?""")
          emitted must include("""let repeated = prompt("x") as OrderId*""")
          // The specific regression this case guards: the wrapper's own recursion must reach the
          // SAME stripped rendering as the unwrapped case above, not fall back to the keyword-
          // including `AliasedTypeExpression.format`.
          emitted must not include "as type OrderId"
        }

        val regen = parse(emitted, "regen")
        letExpression(regen, "optional") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case Optional(_, ate: AliasedTypeExpression) => ate.pathId.value.last mustBe "OrderId"
              case other => fail(s"expected Optional(AliasedTypeExpression), got $other")
          case other => fail(s"expected a PromptValue, got $other")
        letExpression(regen, "repeated") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case ZeroOrMore(_, ate: AliasedTypeExpression) =>
                ate.pathId.value.last mustBe "OrderId"
              case other => fail(s"expected ZeroOrMore(AliasedTypeExpression), got $other")
          case other => fail(s"expected a PromptValue, got $other")
    }
  }
}
