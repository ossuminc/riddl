package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.PlatformIOContext
import org.scalacheck.Arbitrary
import org.scalatest.{Assertion, TestData}

abstract class StatementsTest(using PlatformIOContext) extends AbstractParsingTest{

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
    "check Arbitrary Statements" in { td => 
      val comment = LiteralString(At.empty, "foo")
      val arb = ArbitraryStatement(At.empty, comment)
      arb.kind must be("Arbitrary Statement")
      arb.format must be(comment.format)
      checkStatement(arb)
    }
    "check Error Statement" in { td =>
      val comment = LiteralString(At.empty, "foo")
      val s = ErrorStatement(At.empty, comment)
      s.kind must be("Error Statement")
      s.format must be(s"error ${comment.format}")
      checkStatement(s)
    }
    "check Focus Statement" in { td =>
      val groupRef = GroupRef(At.empty, "group", PathIdentifier(At.empty, Seq("foo")))
      val s = FocusStatement(At.empty, groupRef)
      s.kind must be("Focus Statement")
      s.format must be(s"focus on ${groupRef.format}")
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
    "check Return Statement" in { td =>
      val value = LiteralString(At.empty, "foo")
      val s = ReturnStatement(At.empty, value)
      s.kind must be("Return Statement")
      s.format must be(s"return ${value.format}")
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
    "check Reply Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val msgRef = CommandRef(At.empty, pathId)
      val s = ReplyStatement(At.empty, msgRef)
      s.kind must be("Reply Statement")
      s.format must be(s"reply command foo")
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
    "check Call Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val functionRef = FunctionRef(At.empty, pathId)
      val s = CallStatement(At.empty, functionRef)
      s.kind must be("Call Statement")
      s.format must be(s"call function foo")
      checkStatement(s)
    }
    "check Foreach Statement" in { td =>
      val pathId = PathIdentifier(At.empty, Seq("foo"))
      val fieldRef = FieldRef(At.empty, pathId)
      val s = ForEachStatement(At.empty, fieldRef, Contents.empty)
      s.kind must be("Foreach Statement")
      s.format must be(s"foreach field foo do\n\n  end")
      checkStatement(s)
    }
    "check If-Then-Else Statement" in { td =>
      val condition = LiteralString(At.empty, "foo")
      val s = IfThenElseStatement(At.empty, condition, Contents.empty, Contents.empty)
      s.kind must be("IfThenElse Statement")
      s.format must be(s"if ${condition.format} then {\n  \n} else {\n  \n}\nend")
      checkStatement(s)
    }
    "check Stop Statement" in { td =>
      val s = StopStatement(At.empty)
      s.kind must be("Stop Statement")
      s.format must be(s"stop")
      checkStatement(s)
    }
    "check Read Statement" in { td =>
      val what = LiteralString(At.empty, "foo")
      val pid = PathIdentifier(At.empty, Seq("foo"))
      val from = TypeRef(At.empty, "type", pid)
      val where = LiteralString(At.empty, "bar")
      val s = ReadStatement(At.empty, "read", what, from, where)
      s.kind must be("Read Statement")
      s.format must be(s"read ${what.format} from ${from.format} where ${where.format}")
      checkStatement(s)
    }
    "check Write Statement" in { td =>
      val what = LiteralString(At.empty, "foo")
      val pid = PathIdentifier(At.empty, Seq("foo"))
      val to = TypeRef(At.empty, "type", pid)
      val s = WriteStatement(At.empty, "put", what, to)
      s.kind must be("Write Statement")
      s.format must be(s"put ${what.format} to ${to.format}")
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
