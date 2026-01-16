/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.{Assertion, TestData}

abstract class StatementsTest(using PlatformContext) extends AbstractParsingTest{

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
      val value = CommandRef(At.empty, pathId)
      val s = MorphStatement(At.empty, entityRef, stateRef, value)
      s.kind must be("Morph Statement")
      s.format must be(s"morph entity foo to state foo with command foo")
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
      val s = LetStatement(At.empty, id, expr)
      s.kind must be("Let Statement")
      s.format must be(s"let ${id.format} = ${expr.format}")
      checkStatement(s)
    }
    "check Code Statement" in { td =>
      val language = LiteralString(At.empty, "scala")
      val body = "for { i <- collection } yield { i.that }"
      val s = CodeStatement(At.empty, language, body)
      s.kind must be("Code Statement")
      s.format must be(s"```${language.s}\n$body```")
      checkStatement(s)
    }

    "include Code Statement" in { (td:TestData) =>
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
          |}""".stripMargin,td)
      TopLevelParser.parseInput(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val clause = AST.getContexts(AST.getTopLevelDomains(root).head).head.handlers.head.clauses.head
          val s: Statement = clause.contents.filter[Statement].head
          s.isInstanceOf[CodeStatement] must be(true)
          val codeStatement = s.asInstanceOf[CodeStatement]
          codeStatement.language.s must be("scala")
          codeStatement.body must be("""val foo: Int = 1
              |        """.stripMargin)

    }

  }
}
