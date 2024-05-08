package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.PassesResult

import java.nio.file.Path

class DataFlowDiagramTest extends ValidatingTest {
  "DataFlowDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
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
                           |Commands -- Type 'DoAThing'(2:41) --> Commands
                           |""".stripMargin
          actual must be(expected)
      }
    }
  }
}
