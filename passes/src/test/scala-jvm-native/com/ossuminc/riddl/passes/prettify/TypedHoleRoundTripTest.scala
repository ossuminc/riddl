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
  // and a `when` condition. Modeled on `TypedHoleTest` (language/.../parsing), the parser-level
  // suite Task 1 added for this same fixture shape.
  private def model: String =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    record Line is { sku: String }
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
      |          set field E.S.line to record Line(sku = prompt("x") as String)
      |          when prompt("x") as Boolean then
      |            do "something"
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

  private def constructorArgPromptValue(root: Root): PromptValue =
    Finder(root)
      .recursiveFindByType[SetStatement]
      .find(_.field match {
        case fr: FieldRef => fr.pathId.value.last == "line"
        case _            => false
      })
      .getOrElse(fail("no `set ... line` statement found"))
      .value match
      case c: Constructor =>
        c.args.headOption.map(_.value) match
          case Some(pv: PromptValue) => pv
          case other                 => fail(s"expected a PromptValue constructor arg, got $other")
      case other => fail(s"expected a Constructor set value, got $other")

  private def whenConditionPromptValue(root: Root): PromptValue =
    Finder(root)
      .recursiveFindByType[WhenStatement]
      .headOption
      .getOrElse(fail("no when statement found"))
      .condition match
      case pv: PromptValue => pv
      case other           => fail(s"expected a PromptValue condition, got $other")

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
            case other                      => fail(s"expected an AliasedTypeExpression, got $other")
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
            case other                      => fail(s"expected an AliasedTypeExpression, got $other")
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

    "survive a prettify round trip as a constructor argument" in { (_: TestData) =>
      val original = parse(model, "orig")
      constructorArgPromptValue(original).typeEx.get mustBe a[String_]

      val emitted = prettify(original)
      withClue(s"emitted source was:\n$emitted\n") {
        emitted must include("""sku = prompt("x") as String""")
      }

      val regen = parse(emitted, "regen")
      constructorArgPromptValue(regen).typeEx.get mustBe a[String_]
    }

    "survive a prettify round trip as a `when` condition" in { (_: TestData) =>
      val original = parse(model, "orig")
      whenConditionPromptValue(original).typeEx.get mustBe a[Bool]

      val emitted = prettify(original)
      withClue(s"emitted source was:\n$emitted\n") {
        emitted must include("""when prompt("x") as Boolean then""")
      }

      val regen = parse(emitted, "regen")
      whenConditionPromptValue(regen).typeEx.get mustBe a[Bool]
    }
  }
}
