/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At, Contents, *}
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Reid's 2026-08-14 ruling: `not` and `!` are synonymous everywhere, as the inverse of a boolean
  * expression. This suite pins the parser half of that ruling (task 1 of the
  * `2026-08-15-not-bang-synonymy` plan): both spellings must build the IDENTICAL AST node, not
  * merely both parse. Task 2 removed `WhenStatement.negated` entirely; this suite never asserted on
  * it and needs no change for that.
  */
abstract class BangNotSynonymyTest(using PlatformContext) extends AbstractParsingTest {

  // A28: parse `body` as the single statement inside a context handler `on init` clause and return
  // that statement. Only parsing runs (no passes), so bare identifiers need not resolve. Mirrors
  // `StatementsTest.parseStmt`.
  private def parseStmt(body: String, td: TestData): Statement = {
    val input = RiddlParserInput(
      s"""domain d is {
         |  context c is {
         |    handler h is {
         |      on init {
         |        $body
         |      }
         |    }
         |  }
         |}""".stripMargin,
      td
    )
    TopLevelParser.parseInput(input) match
      case Left(messages) => fail(messages.justErrors.format)
      case Right(root) =>
        val clause =
          AST.getContexts(AST.getTopLevelDomains(root).head).head.handlers.head.clauses.head
        clause.contents.filter[Statement].head
  }

  /** Strip `At` locations from the small set of node kinds these tests build, so two spellings
    * parsed from source strings of different lengths compare equal on SHAPE rather than on offsets.
    * `RiddlValue` case classes do not override `equals` (unlike `Definition`), so plain `==`
    * includes `loc` unless it is normalized first.
    */
  private def blank(v: RiddlValue): RiddlValue = v match
    case NotExpression(_, expr) => NotExpression(At.empty, blank(expr).asInstanceOf[Value])
    case ComparisonExpression(_, op, left, right) =>
      ComparisonExpression(
        At.empty,
        op,
        blank(left).asInstanceOf[Comparand],
        blank(right).asInstanceOf[Comparand]
      )
    case LogicalExpression(_, op, left, right) =>
      LogicalExpression(
        At.empty,
        op,
        blank(left).asInstanceOf[Value],
        blank(right).asInstanceOf[Value]
      )
    case ValueRef(_, path)        => ValueRef(At.empty, blank(path).asInstanceOf[PathIdentifier])
    case PathIdentifier(_, value) => PathIdentifier(At.empty, value)
    case Identifier(_, value)     => Identifier(At.empty, value)
    case other                    => other

  "`not` and `!`" must {

    "build the identical AST for `when not isValid` / `when !isValid`" in { (td: TestData) =>
      val notForm = parseStmt("when not isValid then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      val bangForm = parseStmt("when !isValid then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      blank(notForm) must be(blank(bangForm))
      notForm mustBe a[NotExpression]
    }

    "build the identical AST for `require not x` / `require !x`" in { (td: TestData) =>
      val notForm = parseStmt("require not x", td).asInstanceOf[RequireStatement].condition
      val bangForm = parseStmt("require !x", td).asInstanceOf[RequireStatement].condition
      blank(notForm.asInstanceOf[RiddlValue]) must be(blank(bangForm.asInstanceOf[RiddlValue]))
      notForm mustBe a[NotExpression]
    }

    "build the identical AST for `let y = not x` / `let y = !x`" in { (td: TestData) =>
      val notForm = parseStmt("let y = not x", td).asInstanceOf[LetStatement].expression
      val bangForm = parseStmt("let y = !x", td).asInstanceOf[LetStatement].expression
      blank(notForm) must be(blank(bangForm))
      notForm mustBe a[NotExpression]
    }

    "build the identical AST for `when not (a and b)` / `when !(a and b)`" in { (td: TestData) =>
      val notForm = parseStmt("when not (a and b) then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      val bangForm = parseStmt("when !(a and b) then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      blank(notForm) must be(blank(bangForm))
      notForm mustBe a[NotExpression]
    }

    "build the identical AST for `when not not a` / `when !!a` (recursion)" in { (td: TestData) =>
      val notForm = parseStmt("when not not a then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      val bangForm = parseStmt("when !!a then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      blank(notForm) must be(blank(bangForm))
      notForm match
        case NotExpression(_, inner) => inner mustBe a[NotExpression]
        case other                   => fail(s"expected a nested NotExpression, got $other")
    }

    "build the identical AST for `when not a > b` / `when !a > b`" in { (td: TestData) =>
      val notForm = parseStmt("when not a > b then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      val bangForm = parseStmt("when !a > b then error \"boom\" end", td)
        .asInstanceOf[WhenStatement]
        .condition
      blank(notForm) must be(blank(bangForm))
      notForm match
        case NotExpression(_, inner) => inner mustBe a[ComparisonExpression]
        case other => fail(s"expected NotExpression(ComparisonExpression), got $other")
    }

    // ---- the `!=` guard: `!` must never swallow the `!` of `!=` ----

    "still parse `a != b` as a comparison, bare" in { (td: TestData) =>
      parseStmt("when a != b then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case ComparisonExpression(_, ComparisonOperator.NE, _, _) => // expected
            case other => fail(s"expected a NE ComparisonExpression, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    "still parse `(a != b)` as a comparison, parenthesised" in { (td: TestData) =>
      parseStmt("when (a != b) then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case ComparisonExpression(_, ComparisonOperator.NE, _, _) => // expected
            case other => fail(s"expected a NE ComparisonExpression, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    "keep `notify` a plain identifier, not the `not` keyword" in { (td: TestData) =>
      parseStmt("let y = notify", td) match
        case ls: LetStatement =>
          ls.expression match
            case vr: ValueRef => vr.path.format must be("notify")
            case other        => fail(s"expected a ValueRef('notify'), got $other")
        case other => fail(s"expected a LetStatement, got $other")
    }
  }
}
