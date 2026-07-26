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
      // Source-compat: the deprecated `ReplyStatement` alias resolves to `YieldStatement`.
      @annotation.nowarn("cat=deprecation")
      val alias: AST.ReplyStatement = s
      alias.kind must be("Yield Statement")
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
      val pattern = LiteralString(At.empty, "pattern")
      val mc = MatchCase(At.empty, pattern, Contents.empty())
      val s = MatchStatement(At.empty, expression, Seq(mc), Contents.empty())
      s.kind must be("Match Statement")
      checkStatement(s)
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
          |    type MyCommand is command { field: String }
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
          letStmt.expression.s must be("MyCommand(field = hello)")
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
          letStmt.expression.s must be("some value")
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
      val s = ForeachStatement(At.empty, element, collection, Contents.empty())
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
          |          prompt "process order"
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
          |    type Order is record { id: String }
          |    type OrderList is many Order
          |    handler h is {
          |      on init {
          |        let batch: OrderList = "orders"
          |        foreach o in batch {
          |          prompt "process order"
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

  }
}
