package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{AST,pc,ec}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.diagrams.mermaid.DataFlowDiagram
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.utils.URL

import java.nio.file.Path
import org.scalatest.TestData
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt 

class DataFlowDiagramTest extends JVMAbstractValidatingTest {
  "DataFlowDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val url = URL.fromCwdPath("language/jvm/src/test/input/everything.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        simpleParseAndValidate(rpi) match {
          case Left(messages) => fail(messages.justErrors.format)
          case Right(passesResult: PassesResult) =>

            val dfd = DataFlowDiagram(passesResult)
            val domains = AST.getTopLevelDomains(passesResult.root)
            val contexts = AST.getContexts(domains.head)
            val actual = dfd.generate(contexts.head)
            val expected =
              """flowchart LR
                |Commands[\"Outlet Source.Commands"\]
                |Commands[/"Inlet Sink.Commands"/]
                |APlant[{"Context Everything.APlant"}]
                |command ACommand["OnMessageClause adaptCommands.command ACommand"]
                |Commands -- Type 'DoAThing' --> Commands
                |""".stripMargin
            actual must be(expected)
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
