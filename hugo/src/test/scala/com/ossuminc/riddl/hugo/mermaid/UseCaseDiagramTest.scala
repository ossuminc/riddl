package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.AST.NamedValue
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.analyses.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.hugo.mermaid.UseCaseDiagramSupport

import java.nio.file.Path
import java.net.URI

class UseCaseDiagramTest extends RunPassTestBase {

  case class TestUCDSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
    private val symTab = passesResult.symbols
    def makeDocLink(namedValue: NamedValue): String = {
      val parents = symTab.parentsOf(namedValue)
      val uri = new URI(
        "http", "example.com", parents.map(_.id.value).mkString("//", "/", "/") + s"${namedValue.id.value}", null, null
      )
      uri.toASCIIString
    }
  }

  "UseCaseDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("hugo/src/test/input/epic.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator())
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
