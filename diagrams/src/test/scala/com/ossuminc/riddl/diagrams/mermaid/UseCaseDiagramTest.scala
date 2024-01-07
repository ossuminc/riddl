package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.diagrams.UseCaseDiagramData
import com.ossuminc.riddl.language.AST.Definition
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase
import com.ossuminc.riddl.passes.PassesResult

import java.nio.file.Path
import java.net.URI

class UseCaseDiagramTest extends RunPassTestBase {

  case class TestUCDSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
    val symTab = passesResult.symbols
    def makeDocLink(definition: Definition): String = {
      val parents = symTab.parentsOf(definition)
      val uri = new URI(
        "http", "example.com", parents.map(_.id.value).mkString("//", "/", "/") + s"${definition.id.value}", null, null
      )
      uri.toASCIIString
    }
  }

  "UseCaseDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("diagrams/src/test/input/epic.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator)
      val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
      maybeDPO match
        case Some(dpo: DiagramsPassOutput) =>
          dpo.userCaseDiagrams.headOption match
            case Some((uc, useCaseDiagramData)) =>
              val useCaseDiagram = UseCaseDiagram(TestUCDSupport(result), useCaseDiagramData)
              val lines = useCaseDiagram.generate
              info(lines.mkString("\n"))
              lines mustNot be(empty)
              succeed
            case None => fail("No use cases or epic")
          end match
        case None => fail("no DiagramsPassOutput")
      end match
    }
  }
}
