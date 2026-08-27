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

/** A20 whole-branch review, finding 2: `PromptValue.ascriptionFormat` (`AST.scala`) handles only 5
  * of the ~18 shapes `typeExpression` can produce; everything else fell to its `case other =>
  * other.format` catch-all, which for several shapes does not reproduce parseable source --
  * `RiddlFileEmitter.emitStatement`/`emitConstant` called `.format` directly, so PRETTIFY emitted
  * that broken text into the actual `.riddl` output. Verified broken before the fix (2026-08-15
  * review): `as any of {...}` -> `as {Red,Green}` (parse error), `as table of T of [3,3]` -> `as
  * table of T(3,3)` (parse error), `as reference to entity E` -> `as entity E` (a DIFFERENT node on
  * re-parse), `as Currency(USD)` -> `as Currency` (parse error, the country arg is mandatory), `as
  * sequence of OrderId` -> `as sequence of type OrderId` (an un-authored keyword).
  *
  * The fix routes a `PromptValue` ascription through `RiddlFileEmitter.emitValue`, which calls
  * `emitTypeExpression` -- the SAME total dispatch every other TypeExpression position in the
  * emitter already uses -- rather than `ascriptionFormat`'s narrower one. This file exercises each
  * of the five broken shapes above through a full parse -> prettify -> re-parse round trip, in a
  * `let` (one of the four positions `emitValue` is wired at).
  */
class TypedHoleAscriptionShapesRoundTripTest extends AbstractValidatingTest {

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

  private def model: String =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    entity Target is { ??? }
      |    command Go is { why: String }
      |    entity E is {
      |      handler H is {
      |        on command Go is {
      |          let enumAsc = prompt("x") as any of { Red, Green }
      |          let tableAsc = prompt("x") as table of Integer of [3,3]
      |          let refAsc = prompt("x") as reference to entity Target
      |          let currencyAsc = prompt("x") as Currency(USD)
      |          let seqAsc = prompt("x") as sequence of OrderId
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "a typed hole ascribed to a shape ascriptionFormat did not cover" should {

    "survive a prettify round trip when ascribed to an Enumeration" in { (_: TestData) =>
      val original = parse(model, "orig")
      letExpression(original, "enumAsc") match
        case pv: PromptValue => pv.typeEx.get mustBe an[Enumeration]
        case other           => fail(s"expected a PromptValue, got $other")

      val emitted = prettify(original)
      val regen = parse(emitted, "regen")
      withClue(s"emitted source was:\n$emitted\n") {
        letExpression(regen, "enumAsc") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case e: Enumeration =>
                e.enumerators.toSeq.map(_.id.value) mustBe Seq("Red", "Green")
              case other => fail(s"expected an Enumeration, got $other")
          case other => fail(s"expected a PromptValue, got $other")
      }
    }

    "survive a prettify round trip when ascribed to a Table" in { (_: TestData) =>
      val original = parse(model, "orig")
      letExpression(original, "tableAsc") match
        case pv: PromptValue => pv.typeEx.get mustBe a[Table]
        case other           => fail(s"expected a PromptValue, got $other")

      val emitted = prettify(original)
      val regen = parse(emitted, "regen")
      withClue(s"emitted source was:\n$emitted\n") {
        letExpression(regen, "tableAsc") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case t: Table => t.dimensions mustBe Seq(3L, 3L)
              case other    => fail(s"expected a Table, got $other")
          case other => fail(s"expected a PromptValue, got $other")
      }
    }

    "survive a prettify round trip when ascribed to an EntityReferenceTypeExpression" in {
      (_: TestData) =>
        val original = parse(model, "orig")
        letExpression(original, "refAsc") match
          case pv: PromptValue => pv.typeEx.get mustBe an[EntityReferenceTypeExpression]
          case other           => fail(s"expected a PromptValue, got $other")

        val emitted = prettify(original)
        val regen = parse(emitted, "regen")
        withClue(s"emitted source was:\n$emitted\n") {
          letExpression(regen, "refAsc") match
            case pv: PromptValue =>
              pv.typeEx.get match
                case er: EntityReferenceTypeExpression =>
                  er.entity.value.last mustBe "Target"
                case other => fail(s"expected an EntityReferenceTypeExpression, got $other")
            case other => fail(s"expected a PromptValue, got $other")
        }
    }

    "survive a prettify round trip when ascribed to a Currency" in { (_: TestData) =>
      val original = parse(model, "orig")
      letExpression(original, "currencyAsc") match
        case pv: PromptValue => pv.typeEx.get mustBe a[Currency]
        case other           => fail(s"expected a PromptValue, got $other")

      val emitted = prettify(original)
      val regen = parse(emitted, "regen")
      withClue(s"emitted source was:\n$emitted\n") {
        letExpression(regen, "currencyAsc") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case c: Currency => c.country mustBe "USD"
              case other       => fail(s"expected a Currency, got $other")
          case other => fail(s"expected a PromptValue, got $other")
      }
    }

    "survive a prettify round trip when ascribed to a Sequence, without an un-authored 'type'" +
      " keyword" in { (_: TestData) =>
        val original = parse(model, "orig")
        letExpression(original, "seqAsc") match
          case pv: PromptValue => pv.typeEx.get mustBe a[Sequence]
          case other           => fail(s"expected a PromptValue, got $other")

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include("""let seqAsc = prompt("x") as sequence of OrderId""")
          emitted must not include "sequence of type OrderId"
        }

        val regen = parse(emitted, "regen")
        letExpression(regen, "seqAsc") match
          case pv: PromptValue =>
            pv.typeEx.get match
              case s: Sequence =>
                s.of match
                  case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
                  case other => fail(s"expected an AliasedTypeExpression, got $other")
              case other => fail(s"expected a Sequence, got $other")
          case other => fail(s"expected a PromptValue, got $other")
      }
  }
}
