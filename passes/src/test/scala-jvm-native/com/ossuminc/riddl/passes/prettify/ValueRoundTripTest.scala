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
      |    record Sum is { total: Integer }
      |    function Add is {
      |      returns record Sum
      |      return record Sum(total = "the total")
      |    }
      |  }
      |  application context UI is {
      |    record Greeting is { text: String }
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

    // A24: a `call function F(args)` value must emit and re-parse to a Call at the same place,
    // preserving the function ref and (named) arguments — including a nested call as an argument.
    "round-trip a `call function F(args)` value through prettify (A24)" in { (td: TestData) =>
      val callSrc =
        """domain d is {
          |  context Calc is {
          |    record Args is { a: Integer, b: Integer }
          |    record Sum is { total: Integer }
          |    function Add is {
          |      requires record Args
          |      returns record Sum
          |      return record Sum(total = "t")
          |    }
          |    function Now is {
          |      returns record Sum
          |      return record Sum(total = "0")
          |    }
          |    function Caller is {
          |      requires record Args
          |      returns record Sum
          |      return call function Add(a = "1", b = "2")
          |    }
          |    function CallerZero is {
          |      returns record Sum
          |      return call function Now()
          |    }
          |  }
          |}
          |""".stripMargin
      val pretty = prettify(parse(callSrc, "callsrc"))
      pretty must include("call function Add(a = \"1\", b = \"2\")")
      pretty must include("call function Now()")

      val regen = parse(pretty, "callregen")
      // A Call is a Value (not a Contents node), so it is reached via its containing ReturnStatement.
      val calls = Finder(regen).recursiveFindByType[ReturnStatement].map(_.value).collect {
        case c: Call => c
      }
      calls.size mustBe 2
      val add = calls
        .find(_.function.pathId.value == Seq("Add"))
        .getOrElse(fail("call of Add lost"))
      add.args.size mustBe 2
      add.args.map(_.name.map(_.value)) mustBe Seq(Some("a"), Some("b"))
      calls.find(_.function.pathId.value == Seq("Now")).map(_.args.size) mustBe Some(0)
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

    "round-trip a structured match (subject, type-case, comparison, guard) through prettify (A29)" in {
      (td: TestData) =>
        val matchSrc =
          """domain d is {
            |  context c is {
            |    handler h is {
            |      on init {
            |        match order.status {
            |          case Shipped { error "s" }
            |          case == Cancelled { error "c" }
            |          case > MaxRetries when count > MaxRetries { error "r" }
            |          default { error "d" }
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        val pretty = prettify(parse(matchSrc, "matchsrc"))
        pretty must include("match order.status {")
        pretty must include("case Shipped {")
        pretty must include("case == Cancelled {")
        pretty must include("case > MaxRetries when count > MaxRetries {")
        val regen = parse(pretty, "matchregen")
        val ms = Finder(regen)
          .recursiveFindByType[MatchStatement]
          .headOption
          .getOrElse(fail("match statement lost"))
        ms.expression.asInstanceOf[ValueRef].path.value mustBe Seq("order", "status")
        ms.cases must have size 3
        ms.cases(0).pattern.asInstanceOf[TypePattern].typeRef.pathId.value mustBe Seq("Shipped")
        ms.cases(1).pattern match
          case ComparisonPattern(_, op, _) => op mustBe ComparisonOperator.EQ
          case other                       => fail(s"expected ComparisonPattern, got $other")
        ms.cases(2).guard mustBe defined
    }
  }
}
