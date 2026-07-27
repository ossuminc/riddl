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

/** A54/A45/A57: RIDDL is reflective — `put`/`return` and the value expressions (constructor,
  * get-value, value-ref) must emit (prettify) and re-parse to the same shape.
  */
class ValueRoundTripTest extends AbstractValidatingTest {

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
      |  context Calc is {
      |    type Sum is record { total: Integer }
      |    function Add is {
      |      returns record Sum
      |      return record Sum(total = "the total")
      |    }
      |  }
      |  application context UI is {
      |    type Greeting is record { text: String }
      |    command Refresh is { ??? }
      |    group Main is {
      |      form Entry acquires type Greeting
      |      output Panel presents type Greeting
      |    }
      |    handler Screen is {
      |      on command Refresh {
      |        put get from input Entry to output Panel
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "value expressions" should {
    "round-trip put/return and their values through prettify" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("return record Sum(total = \"the total\")")
      pretty must include("put get from input Entry to output Panel")

      val regen = parse(pretty, "regen")
      val ret = Finder(regen)
        .recursiveFindByType[ReturnStatement]
        .headOption
        .getOrElse(fail("return statement lost"))
      ret.value match
        case c: Constructor =>
          c.ref.isInstanceOf[RecordRef] mustBe true
          c.args.size mustBe 1
          c.args.head.name.map(_.value) mustBe Some("total")
        case other => fail(s"expected a Constructor return value, got $other")

      val put = Finder(regen)
        .recursiveFindByType[PutStatement]
        .headOption
        .getOrElse(fail("put statement lost"))
      put.output.pathId.value mustBe Seq("Panel")
      put.value match
        case gv: GetValue =>
          gv.source match
            case ir: InputRef => ir.pathId.value mustBe Seq("Entry")
            case other        => fail(s"expected InputRef source, got $other")
        case other => fail(s"expected a GetValue put value, got $other")
    }

    "round-trip a nested boolean expression through prettify preserving structure (A28)" in {
      (td: TestData) =>
        val boolSrc =
          """domain d is {
            |  context c is {
            |    handler h is {
            |      on init {
            |        let x = (a or b) and not c
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        val pretty = prettify(parse(boolSrc, "boolsrc"))
        val regen = parse(pretty, "boolregen")
        val let = Finder(regen)
          .recursiveFindByType[LetStatement]
          .headOption
          .getOrElse(fail("let statement lost"))
        // Structure must survive: And(Or(a, b), Not(c)) — precedence preserved via parenthesization.
        let.expression match
          case LogicalExpression(_, LogicalOperator.And, left, right) =>
            left match
              case LogicalExpression(_, LogicalOperator.Or, a, b) =>
                a.asInstanceOf[ValueRef].path.value mustBe Seq("a")
                b.asInstanceOf[ValueRef].path.value mustBe Seq("b")
              case other => fail(s"expected Or on the left, got $other")
            right match
              case NotExpression(_, inner) =>
                inner.asInstanceOf[ValueRef].path.value mustBe Seq("c")
              case other => fail(s"expected Not on the right, got $other")
          case other => fail(s"expected And at the root after round-trip, got $other")
    }

    // A28 slice 2 / review M3: a `when a > b and not c` condition must survive prettify with its
    // ComparisonExpression/LogicalExpression/NotExpression structure intact.
    "round-trip a `when a > b and not c` condition through prettify (A28 s2)" in { (td: TestData) =>
      val whenSrc =
        """domain d is {
            |  context c is {
            |    handler h is {
            |      on init {
            |        when a > b and not c then error "boom" end
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
      val pretty = prettify(parse(whenSrc, "whensrc"))
      val regen = parse(pretty, "whenregen")
      val ws = Finder(regen)
        .recursiveFindByType[WhenStatement]
        .headOption
        .getOrElse(fail("when statement lost"))
      ws.condition match
        case LogicalExpression(_, LogicalOperator.And, left, right) =>
          left match
            case ComparisonExpression(_, ComparisonOperator.GT, a, b) =>
              a.asInstanceOf[ValueRef].path.value mustBe Seq("a")
              b.asInstanceOf[ValueRef].path.value mustBe Seq("b")
            case other => fail(s"expected a > b comparison on the left, got $other")
          right match
            case NotExpression(_, inner) =>
              inner.asInstanceOf[ValueRef].path.value mustBe Seq("c")
            case other => fail(s"expected not c on the right, got $other")
        case other => fail(s"expected And condition after round-trip, got $other")
    }

    // A17: a bare boolean value reference (single name AND dotted path) must survive prettify as a
    // ValueRef condition (not dropped, not relocated, not degraded to an Identifier/LiteralString).
    "round-trip a bare boolean `when <ref>` condition through prettify (A17)" in { (td: TestData) =>
      val whenSrc =
        """domain d is {
          |  context c is {
          |    handler h is {
          |      on init {
          |        when flag then error "one" end
          |        when order.isPaid then error "two" end
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val pretty = prettify(parse(whenSrc, "whenrefsrc"))
      val regen = parse(pretty, "whenrefregen")
      val whens = Finder(regen).recursiveFindByType[WhenStatement]
      whens.size mustBe 2
      whens.head.condition match
        case vr: ValueRef => vr.path.value mustBe Seq("flag")
        case other        => fail(s"expected a ValueRef condition, got $other")
      whens(1).condition match
        case vr: ValueRef => vr.path.value mustBe Seq("order", "isPaid")
        case other        => fail(s"expected a dotted ValueRef condition, got $other")
    }
  }
}
