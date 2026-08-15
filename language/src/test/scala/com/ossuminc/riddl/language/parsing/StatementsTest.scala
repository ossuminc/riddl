/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.{Assertion, TestData}

abstract class StatementsTest(using PlatformContext) extends AbstractParsingTest {

  def checkStatement(s: Statement): Assertion = {
    s.loc must be(empty)
    s.isRootContainer must be(false)
    s.isVital must be(false)
    s.isComment must be(false)
    s.isAnonymous must be(true)
    s.isContainer must be(false)
    s.isDefinition must be(false)
    s.isIdentified must be(false)
    s.isProcessor must be(false)
  }
  // A28: parse `body` as the single statement inside a context handler `on init` clause and return
  // that statement. Only parsing runs (no passes), so bare identifiers need not resolve.
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

  private def parseLetExpr(expr: String, td: TestData): Value =
    parseStmt(s"let x = $expr", td).asInstanceOf[LetStatement].expression

  // A28 s3: assert that `let x = <expr>` fails to PARSE (not merely validate). Comparison operands
  // are refs or a bare `NumericLiteral` (A28 was reversed 2026-08-14 to admit the latter); this
  // helper is used to prove the OTHER value kinds — a quoted string, a boolean literal, a
  // constructor — are still rejected as comparands by the parser itself.
  private def parseLetExprFails(expr: String, td: TestData): Assertion = {
    val input = RiddlParserInput(
      s"""domain d is {
         |  context c is {
         |    handler h is {
         |      on init {
         |        let x = $expr
         |      }
         |    }
         |  }
         |}""".stripMargin,
      td
    )
    TopLevelParser.parseInput(input) match
      case Left(messages) => messages.hasErrors must be(true)
      case Right(_)       => fail(s"expected a PARSE error for 'let x = $expr'")
  }

  private def vref(v: RiddlValue): String = v.asInstanceOf[ValueRef].path.value.mkString(".")

  "Statements" must {
    "check Prompt Statements" in { td =>
      val comment = LiteralString(At.empty, "foo")
      val prompt = PromptStatement(At.empty, comment)
      prompt.kind must be("Prompt Statement")
      prompt.format must be(comment.format)
      checkStatement(prompt)
    }
    "check Error Statement" in { td =>
      val comment = LiteralString(At.empty, "foo")
      val s = ErrorStatement(At.empty, comment)
      s.kind must be("Error Statement")
      s.format must be(s"error ${comment.format}")
      checkStatement(s)
    }
    "check Set Statement" in { td =>
      val fieldRef = FieldRef(At.empty, PathIdentifier(At.empty, Seq("foo")))
      val value = LiteralString(At.empty, "foo")
      val s = SetStatement(At.empty, fieldRef, value)
      s.kind must be("Set Statement")
      s.format must be(s"set ${fieldRef.format} to ${value.format}")
      checkStatement(s)
    }
    "check Send Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val msgRef = CommandRef(At.empty, pathId)
      val portletRef = InletRef(At.empty, pathId)
      val s = SendStatement(At.empty, msgRef, portletRef)
      s.kind must be("Send Statement")
      s.format must be(s"send command foo to inlet foo")
      checkStatement(s)
    }
    "check Morph Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val entityRef = EntityRef(At.empty, pathId)
      val stateRef = StateRef(At.empty, pathId)
      val value = RecordRef(At.empty, pathId)
      val s = MorphStatement(At.empty, entityRef, stateRef, value)
      s.kind must be("Morph Statement")
      s.format must be(s"morph entity foo to state foo with record foo")
      checkStatement(s)
    }
    "check Become Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val entityRef = EntityRef(At.empty, pathId)
      val handlerRef = HandlerRef(At.empty, pathId)
      val s = BecomeStatement(At.empty, entityRef, handlerRef)
      s.kind must be("Become Statement")
      s.format must be(s"become entity foo to handler foo")
      checkStatement(s)
    }
    "check Tell Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val entityRef = EntityRef(At.empty, pathId)
      val value = CommandRef(At.empty, pathId)
      val s = TellStatement(At.empty, value, entityRef)
      s.kind must be("Tell Statement")
      s.format must be(s"tell ${value.format} to ${entityRef.format}")
      checkStatement(s)
    }
    "check Yield Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val value = EventRef(At.empty, pathId)
      val s = YieldStatement(At.empty, value)
      s.kind must be("Yield Statement")
      s.format must be(s"yield ${value.format}")
      checkStatement(s)
    }
    "check Reply Statement" in { td =>
      // `ReplyStatement` was `type ReplyStatement = YieldStatement`, a deprecated alias, until
      // 2.0. It is now its own node: `yield` emits an EVENT from a command, `reply` answers a
      // QUERY with its result. The alias assertion that lived here is gone with the alias.
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val value = ResultRef(At.empty, pathId)
      val s = ReplyStatement(At.empty, value)
      s.kind must be("Reply Statement")
      s.format must be(s"reply ${value.format}")
      checkStatement(s)
    }
    "check When Statement" in { td =>
      val condition = LiteralString(At.empty, "condition")
      val s = WhenStatement(At.empty, condition, Contents.empty())
      s.kind must be("When Statement")
      s.format must be(s"when ${condition.format} then\n\n  end")
      checkStatement(s)
    }
    "check Match Statement" in { td =>
      val expression = LiteralString(At.empty, "expression")
      val pattern = LiteralPattern(At.empty, LiteralString(At.empty, "pattern"))
      val mc = MatchCase(At.empty, pattern, None, Contents.empty())
      val s = MatchStatement(At.empty, expression, Seq(mc), Contents.empty())
      s.kind must be("Match Statement")
      checkStatement(s)
    }
    "parse a structured match: value-ref subject, type-case, comparison, guard (A29)" in { td =>
      val s = parseStmt(
        """match order.status {
          |  case Shipped { error "s" }
          |  case == Cancelled { error "c" }
          |  case > MaxRetries when count > MaxRetries { error "r" }
          |  default { error "d" }
          |}""".stripMargin,
        td
      ).asInstanceOf[MatchStatement]
      s.expression mustBe a[ValueRef]
      s.expression.asInstanceOf[ValueRef].path.value.mkString(".") must be("order.status")
      s.cases must have size 3
      // type-case
      s.cases(0).pattern match
        case TypePattern(_, tr) => tr.pathId.value.mkString(".") must be("Shipped")
        case other              => fail(s"expected TypePattern, got $other")
      s.cases(0).guard must be(None)
      // equality comparison pattern
      s.cases(1).pattern match
        case ComparisonPattern(_, op, cmp) =>
          op must be(ComparisonOperator.EQ)
          cmp.asInstanceOf[ValueRef].path.value.mkString(".") must be("Cancelled")
        case other => fail(s"expected ComparisonPattern, got $other")
      // ordering comparison pattern with a guard
      s.cases(2).pattern match
        case ComparisonPattern(_, op, _) => op must be(ComparisonOperator.GT)
        case other                       => fail(s"expected ComparisonPattern, got $other")
      s.cases(2).guard match
        case Some(_: ComparisonExpression) => succeed
        case other => fail(s"expected a guard ComparisonExpression, got $other")
      s.default.isEmpty must be(false)
    }
    "parse a match case with a bare boolean value-ref guard (A29)" in { td =>
      val s = parseStmt(
        """match order.status {
          |  case Shipped when active { error "s" }
          |  default { error "d" }
          |}""".stripMargin,
        td
      ).asInstanceOf[MatchStatement]
      s.cases.head.guard match
        case Some(vr: ValueRef) => vr.path.value.mkString(".") must be("active")
        case other              => fail(s"expected a bare ValueRef guard, got $other")
    }
    "parse a match with a get-from-state subject (A29)" in { td =>
      val s = parseStmt(
        """match get from state S {
          |  case Ready { error "r" }
          |}""".stripMargin,
        td
      ).asInstanceOf[MatchStatement]
      s.expression mustBe a[GetValue]
      s.cases.head.pattern mustBe a[TypePattern]
    }
    "parse a legacy string match unchanged (A29 regression)" in { td =>
      val s = parseStmt(
        """match "orderStatus" {
          |  case "pending" { error "p" }
          |  default { error "u" }
          |}""".stripMargin,
        td
      ).asInstanceOf[MatchStatement]
      s.expression mustBe a[LiteralString]
      s.expression.asInstanceOf[LiteralString].s must be("orderStatus")
      s.cases.head.pattern match
        case LiteralPattern(_, ls) => ls.s must be("pending")
        case other                 => fail(s"expected LiteralPattern, got $other")
    }
    "check Let Statement" in { td =>
      val id = Identifier(At.empty, "foo")
      val expr = LiteralString(At.empty, "value")
      val s = LetStatement(At.empty, id, None, expr)
      s.kind must be("Let Statement")
      s.format must be(s"let ${id.format} = ${expr.format}")
      checkStatement(s)
    }
    "check Let Statement with type annotation" in { td =>
      val id = Identifier(At.empty, "foo")
      val tr = TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("Number")))
      val expr = LiteralString(At.empty, "42")
      val s = LetStatement(At.empty, id, Some(tr), expr)
      s.kind must be("Let Statement")
      s.format must be("let foo: type Number = \"42\"")
      checkStatement(s)
    }
    "parse Let Statement with type annotation" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain LetTest is {
          |  context LetTest is {
          |    command MyCommand is { field: String }
          |    handler h is {
          |      on init {
          |        let myVar: MyCommand = "MyCommand(field = hello)"
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
          val s: Statement = clause.contents.filter[Statement].head
          s.isInstanceOf[LetStatement] must be(true)
          val letStmt = s.asInstanceOf[LetStatement]
          letStmt.identifier.value must be("myVar")
          letStmt.typeRef must not be empty
          letStmt.typeRef.get.pathId.value must be(Seq("MyCommand"))
          letStmt.expression.asInstanceOf[LiteralString].s must be("MyCommand(field = hello)")
    }
    "parse Let Statement without type annotation" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain LetTest2 is {
          |  context LetTest2 is {
          |    handler h is {
          |      on init {
          |        let myVar = "some value"
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
          val s: Statement = clause.contents.filter[Statement].head
          s.isInstanceOf[LetStatement] must be(true)
          val letStmt = s.asInstanceOf[LetStatement]
          letStmt.identifier.value must be("myVar")
          letStmt.typeRef must be(None)
          letStmt.expression.asInstanceOf[LiteralString].s must be("some value")
    }
    "check Code Statement" in { td =>
      val language = LiteralString(At.empty, "scala")
      val body = "for { i <- collection } yield { i.that }"
      val s = CodeStatement(At.empty, language, body)
      s.kind must be("Code Statement")
      s.format must be(s"```${language.s}\n$body```")
      checkStatement(s)
    }

    "include Code Statement" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain CodeStatements is {
          |  context CodeStatements is {
          |    handler h is {
          |      on init {
          |        ```scala
          |          val foo: Int = 1
          |        ```
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
          val s: Statement = clause.contents.filter[Statement].head
          s.isInstanceOf[CodeStatement] must be(true)
          val codeStatement = s.asInstanceOf[CodeStatement]
          codeStatement.language.s must be("scala")
          codeStatement.body must be("""val foo: Int = 1
              |        """.stripMargin)

    }

    "check Foreach Statement" in { td =>
      val element = Identifier(At.empty, "item")
      val collection = FieldRef(At.empty, PathIdentifier(At.empty, Seq("State", "orders")))
      val s = ForeachStatement(At.empty, element, None, collection, Contents.empty())
      s.kind must be("Foreach Statement")
      s.format must be(s"foreach ${element.format} in ${collection.format} { … }")
      checkStatement(s)
    }

    "parse Foreach Statement over a field" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ForeachTest is {
          |  context ForeachTest is {
          |    handler h is {
          |      on init {
          |        foreach o in field S.orders {
          |          do "process order"
          |        }
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
          val s: Statement = clause.contents.filter[Statement].head
          s.isInstanceOf[ForeachStatement] must be(true)
          val fs = s.asInstanceOf[ForeachStatement]
          fs.element.value must be("o")
          fs.collection.isInstanceOf[FieldRef] must be(true)
          fs.collection.asInstanceOf[FieldRef].pathId.value must be(Seq("S", "orders"))
          fs.doStatements.filter[Statement].size must be(1)
    }

    "parse Foreach Statement over a local" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ForeachTest2 is {
          |  context ForeachTest2 is {
          |    record Order is { id: String }
          |    type OrderList is many Order
          |    handler h is {
          |      on init {
          |        let batch: OrderList = "orders"
          |        foreach o in batch {
          |          do "process order"
          |        }
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
          val stmts = clause.contents.filter[Statement]
          val fs = stmts
            .collectFirst { case f: ForeachStatement => f }
            .getOrElse(
              fail("expected a ForeachStatement")
            )
          fs.element.value must be("o")
          fs.collection.isInstanceOf[Identifier] must be(true)
          fs.collection.asInstanceOf[Identifier].value must be("batch")
    }

    "check Constructor / ValueRef / GetValue formatting (A54)" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("Foo"))
      val vr = ValueRef(At.empty, PathIdentifier(At.empty, Seq("x")))
      vr.kind must be("Value Reference")
      vr.format must be("x")
      val gv = GetValue(At.empty, StateRef(At.empty, PathIdentifier(At.empty, Seq("S"))))
      gv.kind must be("Get Value")
      gv.format must be("get from state S")
      val arg1 = ConstructorArg(At.empty, None, LiteralString(At.empty, "hi"))
      val arg2 = ConstructorArg(At.empty, Some(Identifier(At.empty, "b")), vr)
      arg1.format must be("\"hi\"")
      arg2.format must be("b = x")
      val c = Constructor(At.empty, CommandRef(At.empty, pathId), Seq(arg1, arg2))
      c.kind must be("Constructor")
      c.format must be("command Foo(\"hi\", b = x)")
    }

    "check Put / Return Statement formatting (A45/A57)" in { td =>
      val out = OutputRef(At.empty, "output", PathIdentifier(At.empty, Seq("O")))
      val v = LiteralString(At.empty, "hi")
      val put = PutStatement(At.empty, v, out)
      put.kind must be("Put Statement")
      put.format must be("put \"hi\" to output O")
      checkStatement(put)
      val ret = ReturnStatement(At.empty, v)
      ret.kind must be("Return Statement")
      ret.format must be("return \"hi\"")
      checkStatement(ret)
    }

    "parse a put statement with get-from-input in a context handler (A45)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain PutTest is {
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
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseInput(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val clause = AST
            .getContexts(AST.getTopLevelDomains(root).head)
            .head
            .handlers
            .head
            .clauses
            .head
          val s = clause.contents.filter[Statement].collectFirst { case p: PutStatement => p }
          s must not be empty
          s.get.output.pathId.value must be(Seq("Panel"))
          s.get.value.isInstanceOf[GetValue] must be(true)
    }

    "parse a return statement with a record constructor in a function (A57)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain RetTest is {
          |  context Calc is {
          |    record Sum is { total: Integer }
          |    function Add is {
          |      returns record Sum
          |      return record Sum(total = "the total")
          |    }
          |  }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseInput(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val fn = AST.getContexts(AST.getTopLevelDomains(root).head).head.functions.head
          val s = fn.contents.filter[Statement].collectFirst { case r: ReturnStatement => r }
          s must not be empty
          val ctor = s.get.value
          ctor.isInstanceOf[Constructor] must be(true)
          val c = ctor.asInstanceOf[Constructor]
          c.ref.isInstanceOf[RecordRef] must be(true)
          c.args.size must be(1)
          c.args.head.name.map(_.value) must be(Some("total"))
    }

    "ban a return statement outside a function (A57)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain RetBan is {
          |  context c is {
          |    handler h is {
          |      on init {
          |        return record Foo()
          |      }
          |    }
          |  }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseInput(input) match
        case Left(messages) => messages.hasErrors must be(true)
        case Right(_)       => fail("expected a parse ban for 'return' outside a function")
    }

    "parse each comparison operator (A28)" in { (td: TestData) =>
      val ops = Seq(
        "==" -> ComparisonOperator.EQ,
        "!=" -> ComparisonOperator.NE,
        "<" -> ComparisonOperator.LT,
        ">" -> ComparisonOperator.GT,
        "<=" -> ComparisonOperator.LE,
        ">=" -> ComparisonOperator.GE
      )
      ops.foreach { case (sym, op) =>
        parseLetExpr(s"a $sym b", td) match
          case ce: ComparisonExpression =>
            ce.op must be(op)
            vref(ce.left) must be("a")
            vref(ce.right) must be("b")
          case other => fail(s"expected a ComparisonExpression for '$sym', got $other")
      }
    }

    "parse `or`/`and` precedence: a or b and c => Or(a, And(b, c)) (A28)" in { (td: TestData) =>
      parseLetExpr("a or b and c", td) match
        case LogicalExpression(_, LogicalOperator.Or, left, right) =>
          vref(left) must be("a")
          right match
            case LogicalExpression(_, LogicalOperator.And, b, c) =>
              vref(b) must be("b"); vref(c) must be("c")
            case other => fail(s"expected And on the right, got $other")
        case other => fail(s"expected Or at the root, got $other")
    }

    "parse `not` precedence: not a and b => And(Not(a), b) (A28)" in { (td: TestData) =>
      parseLetExpr("not a and b", td) match
        case LogicalExpression(_, LogicalOperator.And, left, right) =>
          left match
            case NotExpression(_, inner) => vref(inner) must be("a")
            case other                   => fail(s"expected Not on the left, got $other")
          vref(right) must be("b")
        case other => fail(s"expected And at the root, got $other")
    }

    "parenthesization overrides precedence: (a or b) and c => And(Or(a, b), c) (A28)" in {
      (td: TestData) =>
        parseLetExpr("(a or b) and c", td) match
          case LogicalExpression(_, LogicalOperator.And, left, right) =>
            left match
              case LogicalExpression(_, LogicalOperator.Or, a, b) =>
                vref(a) must be("a"); vref(b) must be("b")
              case other => fail(s"expected Or on the left, got $other")
            vref(right) must be("c")
          case other => fail(s"expected And at the root, got $other")
    }

    "parse a boolean literal: true (A28)" in { (td: TestData) =>
      parseLetExpr("true", td) match
        case BooleanLiteral(_, v) => v must be(true)
        case other                => fail(s"expected a BooleanLiteral, got $other")
      parseLetExpr("false", td) match
        case BooleanLiteral(_, v) => v must be(false)
        case other                => fail(s"expected a BooleanLiteral, got $other")
    }

    "parse a combined expression: a > b and not c (A28)" in { (td: TestData) =>
      parseLetExpr("a > b and not c", td) match
        case LogicalExpression(_, LogicalOperator.And, left, right) =>
          left match
            case ComparisonExpression(_, ComparisonOperator.GT, a, b) =>
              vref(a) must be("a"); vref(b) must be("b")
            case other => fail(s"expected a comparison on the left, got $other")
          right match
            case NotExpression(_, inner) => vref(inner) must be("c")
            case other                   => fail(s"expected Not on the right, got $other")
        case other => fail(s"expected And at the root, got $other")
    }

    "leave a plain value unwrapped (regression): let x = someField => ValueRef (A28)" in {
      (td: TestData) =>
        // A bare value must parse to exactly its atom, NOT a BooleanExpression wrapper.
        parseLetExpr("someField", td) mustBe a[ValueRef]
        vref(parseLetExpr("someField", td)) must be("someField")
        parseLetExpr("\"hello\"", td) mustBe a[LiteralString]
        // Operator substrings in identifiers stay identifiers (word boundary): `android`, `notify`.
        parseLetExpr("android", td) mustBe a[ValueRef]
        vref(parseLetExpr("android", td)) must be("android")
        parseLetExpr("notify", td) mustBe a[ValueRef]
    }

    "parse `set f to a == b` as a comparison (A28)" in { (td: TestData) =>
      parseStmt("set field F to a == b", td) match
        case SetStatement(_, _, ce: ComparisonExpression) =>
          ce.op must be(ComparisonOperator.EQ)
          vref(ce.left) must be("a")
          vref(ce.right) must be("b")
        case other => fail(s"expected a SetStatement with a comparison, got $other")
    }

    // ---- A28 slice 3: comparison operands are type-safe (refs, or a bare numeric literal) ----

    "reject a string-literal comparison operand at PARSE: count > \"5\" (A28 s3)" in {
      (td: TestData) => parseLetExprFails("count > \"5\"", td)
    }

    "reject a boolean-literal comparison operand at PARSE: count > true (A28 s3)" in {
      (td: TestData) => parseLetExprFails("count > true", td)
    }

    // A28 was reversed 2026-08-14: a bare number is now a legal comparand (draws a StyleWarning in
    // validation, not a parse error). See the doc on AST.Comparand for why.
    "parse a bare-number comparison operand: count > 5 (A28, widened 2026-08-14)" in {
      (td: TestData) =>
        parseLetExpr("count > 5", td) match
          case ComparisonExpression(_, ComparisonOperator.GT, left, right) =>
            vref(left) must be("count")
            right match
              case nl: NumericLiteral => nl.text must be("5")
              case other              => fail(s"expected a NumericLiteral right operand, got $other")
          case other => fail(s"expected a ComparisonExpression, got $other")
    }

    // The ordering that was inert until this task: `value` tries `booleanExpr` (hence `comparison`,
    // hence `comparand`) BEFORE `numericLiteral`. Without it, `5` would be consumed whole by
    // `numericLiteral` and `> 3` would be left dangling instead of completing a comparison.
    "parse `5 > 3` as a comparison, not a bare NumericLiteral (ordering, A28)" in { (td: TestData) =>
      parseLetExpr("5 > 3", td) match
        case ComparisonExpression(_, ComparisonOperator.GT, left, right) =>
          left match
            case nl: NumericLiteral => nl.text must be("5")
            case other              => fail(s"expected a NumericLiteral left operand, got $other")
          right match
            case nl: NumericLiteral => nl.text must be("3")
            case other              => fail(s"expected a NumericLiteral right operand, got $other")
        case other => fail(s"expected a ComparisonExpression, got $other")
    }

    "reject a constructor comparison operand at PARSE: count > R(1) (A28 s3)" in { (td: TestData) =>
      parseLetExprFails("count > R(1)", td)
    }

    "parse a comparison against a `constant` ref: count > constant Max (A28 s3)" in {
      (td: TestData) =>
        parseLetExpr("count > constant Max", td) match
          case ComparisonExpression(_, ComparisonOperator.GT, left, right) =>
            vref(left) must be("count")
            right match
              case cr: ConstantRef => cr.pathId.value.mkString(".") must be("Max")
              case other           => fail(s"expected a ConstantRef right operand, got $other")
          case other => fail(s"expected a ComparisonExpression, got $other")
    }

    "group with parentheses: (a and b) or c => Or(And(a, b), c) (A28 s3)" in { (td: TestData) =>
      parseLetExpr("(a and b) or c", td) match
        case LogicalExpression(_, LogicalOperator.Or, left, right) =>
          left match
            case LogicalExpression(_, LogicalOperator.And, a, b) =>
              vref(a) must be("a"); vref(b) must be("b")
            case other => fail(s"expected And on the left, got $other")
          vref(right) must be("c")
        case other => fail(s"expected Or at the root, got $other")
    }

    // ---- A28 slice 2: boolean expressions as when/require/invariant conditions ----

    "parse `when a > b` as a BooleanExpression condition (A28 s2)" in { (td: TestData) =>
      parseStmt("when a > b then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case ce: ComparisonExpression =>
              ce.op must be(ComparisonOperator.GT)
              vref(ce.left) must be("a")
              vref(ce.right) must be("b")
            case other => fail(s"expected a ComparisonExpression condition, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    "parse `when x and y` as a BooleanExpression condition (A28 s2)" in { (td: TestData) =>
      parseStmt("when x and y then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case LogicalExpression(_, LogicalOperator.And, left, right) =>
              vref(left) must be("x"); vref(right) must be("y")
            case other => fail(s"expected an And LogicalExpression condition, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    "keep legacy `when` forms unchanged (regression, A28 s2)" in { (td: TestData) =>
      // A quoted pseudo-code condition stays a LiteralString.
      parseStmt("when \"newPrice > 0\" then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition mustBe a[LiteralString]
          ws.negated must be(false)
        case other => fail(s"expected a WhenStatement, got $other")
      // A negated bare ref stays an Identifier with negated=true (the `! identifier` legacy arm).
      parseStmt("when !flag then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition mustBe a[Identifier]
          ws.negated must be(true)
        case other => fail(s"expected a WhenStatement, got $other")
    }

    // ---- A17: a bare boolean value reference is a first-class `when` condition ----

    "parse `when flag` (single name) as a ValueRef condition (A17)" in { (td: TestData) =>
      parseStmt("when flag then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case vr: ValueRef =>
              vr.path.format must be("flag")
              ws.negated must be(false)
            case other => fail(s"expected a ValueRef condition, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    "parse `when order.isPaid` (dotted path) as a ValueRef condition (A17)" in { (td: TestData) =>
      parseStmt("when order.isPaid then error \"boom\" end", td) match
        case ws: WhenStatement =>
          ws.condition match
            case vr: ValueRef =>
              vr.path.format must be("order.isPaid")
              ws.negated must be(false)
            case other => fail(s"expected a ValueRef condition, got $other")
        case other => fail(s"expected a WhenStatement, got $other")
    }

    // NOTE: `require count == 0` (a bare numeric literal operand) does NOT parse — slice-1's boolean
    // atom has no numeric-literal form (operands are refs / quoted literals / true / false). A
    // numeric literal would be a new AST node touching every reflective surface, i.e. a slice-1
    // grammar change, out of scope here. Comparing two refs exercises the same widening.
    "parse `require count == total` as a BooleanExpression condition (A28 s2)" in {
      (td: TestData) =>
        parseStmt("require count == total", td) match
          case rs: RequireStatement =>
            rs.condition match
              case ce: ComparisonExpression =>
                ce.op must be(ComparisonOperator.EQ)
                vref(ce.left) must be("count")
                vref(ce.right) must be("total")
              case other => fail(s"expected a ComparisonExpression condition, got $other")
          case other => fail(s"expected a RequireStatement, got $other")
    }

    "keep legacy `require` forms unchanged (regression, A28 s2)" in { (td: TestData) =>
      parseStmt("require \"balance >= amount\"", td) match
        case rs: RequireStatement => rs.condition mustBe a[LiteralString]
        case other                => fail(s"expected a RequireStatement, got $other")
      parseStmt("require invariant MyInv", td) match
        case rs: RequireStatement =>
          rs.condition mustBe a[InvariantRef]
          rs.condition.asInstanceOf[InvariantRef].pathId.value must be(Seq("MyInv"))
        case other => fail(s"expected a RequireStatement, got $other")
    }

    "parse `invariant X is a > b` as a BooleanExpression condition (A28 s2)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          |  context c is {
          |    entity e is {
          |      invariant nonNeg is a > b
          |      invariant legacy is "x must be >= 0"
          |    }
          |  }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseInput(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val entity = Finder(AST.getTopLevelDomains(root).head.contents)
            .recursiveFindByType[Entity]
            .head
          val invs = entity.invariants
          invs.find(_.id.value == "nonNeg").flatMap(_.condition) match
            case Some(ce: ComparisonExpression) =>
              ce.op must be(ComparisonOperator.GT)
              vref(ce.left) must be("a"); vref(ce.right) must be("b")
            case other => fail(s"expected a ComparisonExpression condition, got $other")
          // Legacy quoted pseudo-code condition stays a LiteralString.
          invs.find(_.id.value == "legacy").flatMap(_.condition) match
            case Some(_: LiteralString) => succeed
            case other                  => fail(s"expected a LiteralString condition, got $other")
    }

    "ban a put statement outside a context handler (A45)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain PutBan is {
          |  context c is {
          |    entity e is {
          |      handler h is {
          |        on init {
          |          put get from state S to output O
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseInput(input) match
        case Left(messages) => messages.hasErrors must be(true)
        case Right(_)       => fail("expected a parse ban for 'put' outside a context handler")
    }

    // Fix B (2026-08-15, docs/superpowers/plans/2026-08-15-three-task-fixes.md; report
    // task/2026-08-14-value-ref-starting-with-to-fails-to-parse.md). `Readability.readable` had no
    // word boundary, so `to` matched as a PREFIX of any longer identifier. `boundMessageValue`
    // guards its bare-path arm with `!to`, meant to read "not the word `to`" but actually reading
    // "does not start with the two characters `t` `o`" -- so `tell tourCompleted to …` died with
    // "Expected one of (command | event | query | result)", naming a message kind instead of the
    // real cause. Table-driven from the report's own repro AND its negative controls: identifiers
    // starting with OTHER letter pairs were never broken, which is what proves this was `to`-
    // specific rather than a general keyword-prefix problem -- see `readable`'s doc comment for why
    // the fix widens to all twelve readability words rather than patching just this one guard.
    def boundOperandModel(binding: String, stmt: String): String =
      s"""domain d is {
         |  context c is {
         |    command Foo is { a: Integer }
         |    entity target is {
         |      handler In is {
         |        on command d.c.Foo is { do "handle" }
         |      }
         |    }
         |    entity e is {
         |      outlet emitted is command d.c.Foo
         |      handler Ops is {
         |        on $binding: command d.c.Foo is { $stmt }
         |      }
         |    }
         |  }
         |}
         |""".stripMargin

    val toPrefixCases = Seq(
      "tourCompleted",      // reported: message TourCompleted
      "toleranceEvaluated", // reported: message ToleranceEvaluated
      "totalX" // shape of the third reported collision (TouchpointRecorded), condensed
    )
    val controlCases = Seq(
      "abcCompleted", // control: does not start with `to`
      "termX",        // control: starts with `te`, not `to`
      "typeX"         // control: starts with `ty`, not `to`
    )

    (toPrefixCases ++ controlCases).foreach { binding =>
      s"parse a bound operand named '$binding' in `tell` (Fix B)" in { (td: TestData) =>
        val src = boundOperandModel(binding, s"tell $binding to entity d.c.target")
        TopLevelParser.parseInput(RiddlParserInput(src, td)) match
          case Left(messages) =>
            fail(s"tell with binding '$binding' failed to parse: ${messages.justErrors.format}")
          case Right(_) => succeed
      }
      s"parse a bound operand named '$binding' in `send` (Fix B)" in { (td: TestData) =>
        val src = boundOperandModel(binding, s"send $binding to outlet d.c.e.emitted")
        TopLevelParser.parseInput(RiddlParserInput(src, td)) match
          case Left(messages) =>
            fail(s"send with binding '$binding' failed to parse: ${messages.justErrors.format}")
          case Right(_) => succeed
      }
    }

  }
}
