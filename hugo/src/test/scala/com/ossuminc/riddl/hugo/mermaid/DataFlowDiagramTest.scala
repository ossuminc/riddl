package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.diagrams.mermaid.DataFlowDiagram
import java.nio.file.Path
import org.scalatest.TestData

class DataFlowDiagramTest extends ValidatingTest {
  "DataFlowDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val path = Path.of("language/src/test/input/everything.riddl")
      val input = RiddlParserInput.rpiFromPath(path)
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(passesResult: PassesResult) =>

          val dfd = DataFlowDiagram(passesResult)
          val domains = AST.getTopLevelDomains(passesResult.root)
          val contexts = AST.getContexts(domains.head)
          val actual = dfd.generate(contexts.head)
          val expected = """flowchart LR
                           |Commands[\"Outlet Source.Commands"\]
                           |Commands[/"Inlet Sink.Commands"/]
                           |On command ACommand["OnMessageClause adaptCommands.On command ACommand"]
                           |Commands -- Type 'DoAThing'(2:41) --> Commands
                           |""".stripMargin
          actual must be(expected)
      }
    }
  }
}
