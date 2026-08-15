/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** A20: typed holes — `prompt("…") as <type>` ascribes a type to an AI-computed value, so the seam
  * between RIDDL's deterministic tier and its AI tier is checkable. Unascribed `prompt("…")`
  * (`typeEx == None`) remains valid and unchanged.
  */
// ABSTRACT with `(using PlatformContext)`, matching every sibling in this directory. ScalaTest
// cannot instantiate a suite that takes parameters, so the concrete subclasses live in the two
// platform aggregators (JVMNativeTests.scala, JSTests.scala); without them this suite silently
// never runs.
abstract class TypedHoleTest(using PlatformContext) extends AbstractParsingTest {

  // One model exercising the ascription in four positions at once: a `let`, a constructor
  // argument, a `set`, and a `when` condition -- plus an unascribed `prompt(...)` for comparison.
  private def model: String =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    record Line is { sku: String }
      |    command Add is { sku: String }
      |    entity E is {
      |      record Data is { line: Line, qty: Integer }
      |      state S of record Data
      |      handler H is {
      |        on command Add is {
      |          let plain = prompt("x")
      |          let typed = prompt("x") as Real
      |          let aliased = prompt("x") as OrderId
      |          set field E.S.line to record Line(sku = prompt("x") as String)
      |          set field E.S.qty to prompt("x") as Integer
      |          when prompt("x") as Boolean then
      |            do "something"
      |          end
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def parsedRoot(src: String, td: TestData): Root = {
    val input = RiddlParserInput(src, td)
    TopLevelParser.parseInput(input, true) match
      case Left(msgs)   => fail(s"parse failed:\n${msgs.format}")
      case Right(root)  => root
  }

  private def letExpression(root: Root, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let statement named '$name' found"))
      .expression

  "prompt(...)" should {

    "parse with typeEx == None when unascribed (unchanged behaviour)" in { (td: TestData) =>
      letExpression(parsedRoot(model, td), "plain") match
        case pv: PromptValue =>
          pv.prompt.s mustBe "x"
          pv.typeEx mustBe None
        case other => fail(s"expected a PromptValue, got $other")
    }

    "parse with typeEx a predefined type when ascribed with `as Real`" in { (td: TestData) =>
      letExpression(parsedRoot(model, td), "typed") match
        case pv: PromptValue =>
          pv.prompt.s mustBe "x"
          pv.typeEx mustBe a[Some[?]]
          pv.typeEx.get mustBe a[Real]
        case other => fail(s"expected a PromptValue, got $other")
    }

    "parse with typeEx an aliased type reference when ascribed with a user type" in {
      (td: TestData) =>
        letExpression(parsedRoot(model, td), "aliased") match
          case pv: PromptValue =>
            pv.prompt.s mustBe "x"
            pv.typeEx.get match
              case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
              case other => fail(s"expected an AliasedTypeExpression, got $other")
          case other => fail(s"expected a PromptValue, got $other")
    }

    "survive the ascription as a constructor argument value" in { (td: TestData) =>
      // Finder's recursive walk does not descend into a SetStatement's `value` field (only a few
      // statement shapes carrying nested statement LISTS get special-cased -- see Finder.scala),
      // so the SetStatement itself is located first and its Constructor value read directly, same
      // technique as WidenedValueRoundTripTest.
      val root = parsedRoot(model, td)
      val sets = Finder(root).recursiveFindByType[SetStatement]
      val lineSet = sets
        .find(_.field match {
          case fr: FieldRef => fr.pathId.value.last == "line"
          case _            => false
        })
        .getOrElse(fail("no `set ... line` statement found"))
      lineSet.value match
        case c: Constructor =>
          c.args.headOption.map(_.value) match
            case Some(pv: PromptValue) =>
              pv.prompt.s mustBe "x"
              pv.typeEx.get mustBe a[String_]
            case other => fail(s"expected a PromptValue arg, got $other")
        case other => fail(s"expected a Constructor set value, got $other")
    }

    "survive the ascription as a `set` value" in { (td: TestData) =>
      val root = parsedRoot(model, td)
      val sets = Finder(root).recursiveFindByType[SetStatement]
      val qtySet = sets
        .find(_.field match {
          case fr: FieldRef => fr.pathId.value.last == "qty"
          case _            => false
        })
        .getOrElse(fail("no `set ... qty` statement found"))
      qtySet.value match
        case pv: PromptValue =>
          pv.prompt.s mustBe "x"
          pv.typeEx.get mustBe a[Integer]
        case other => fail(s"expected a PromptValue set value, got $other")
    }

    "survive the ascription as a `when` condition" in { (td: TestData) =>
      val root = parsedRoot(model, td)
      val when = Finder(root)
        .recursiveFindByType[WhenStatement]
        .headOption
        .getOrElse(fail("no when statement found"))
      when.condition match
        case pv: PromptValue =>
          pv.prompt.s mustBe "x"
          pv.typeEx.get mustBe a[Bool]
        case other => fail(s"expected a PromptValue condition, got $other")
    }

    "leave the deprecated parenless `prompt` STATEMENT form unaffected" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    entity E is {
          |      handler H is {
          |        on init is {
          |          prompt "do the thing"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val root = parsedRoot(src, td)
      Finder(root).recursiveFindByType[PromptStatement].headOption match
        case Some(ps) => ps.what.s mustBe "do the thing"
        case None     => fail("no PromptStatement found")
    }

    "leave the `do \"...\"` action statement form unaffected" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    entity E is {
          |      handler H is {
          |        on init is {
          |          do "do the thing"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val root = parsedRoot(src, td)
      Finder(root).recursiveFindByType[PromptStatement].headOption match
        case Some(ps) => ps.what.s mustBe "do the thing"
        case None     => fail("no PromptStatement found")
    }
  }
}
