package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.{DiagramsPass,DiagramsPassOutput}
import com.ossuminc.riddl.diagrams.UseCaseDiagramData
import com.ossuminc.riddl.language.AST.Definition
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase
import com.ossuminc.riddl.passes.PassesResult

import java.nio.file.Path

class UseCaseDiagramTest extends RunPassTestBase {

  case class TestUCDSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
    val symTab = passesResult.symbols
    def makeDocLink(definition: Definition): String = {
      val parents = symTab.parentsOf(definition)
      s"[$definition.identify](${parents.mkString("\n")}/${definition.id.value}"
    }
  }

  "UseCaseDiagram" should {
    "do something" in {
      val input = RiddlParserInput(Path.of("src/test/input/epic.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator )
      val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
      maybeDPO match
        case Some(dpo: DiagramsPassOutput) =>
          dpo.userCaseDiagrams.headOption match
            case Some((epic, ucdds)) =>
              for {
                useCaseDiagramData: UseCaseDiagramData <- ucdds
              } {
                val useCaseDiagram = UseCaseDiagram(TestUCDSupport(result), useCaseDiagramData)
                useCaseDiagram.toLines mustBe(nonEmpty)
              }

            case None => fail("No use cases or epic")
          end match
        case None => fail("no DiagramsPassOutput")
      end match

      succeed
    }
  }

}
