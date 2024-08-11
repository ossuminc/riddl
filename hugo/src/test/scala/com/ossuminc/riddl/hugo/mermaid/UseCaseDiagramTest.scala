package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.AST.{Definition, Domain, Identifier, NamedValue, PathIdentifier, Root}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{PassInput, PassesOutput, PassesResult, RunPassTestBase}
import com.ossuminc.riddl.analyses.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.diagrams.mermaid.{UseCaseDiagram, UseCaseDiagramSupport}
import org.scalatest.TestData

import java.nio.file.Path
import java.net.URI
import scala.reflect.ClassTag

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
    "generate a simple diagram correctly" in { (td:TestData) =>
      val path  = Path.of("hugo/src/test/input/epic.riddl")
      val input = RiddlParserInput.rpiFromPath(path)
      val result = runPassesWith(input, DiagramsPass.creator())
      val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
      maybeDPO match
        case Some(dpo: DiagramsPassOutput) =>
          dpo.useCaseDiagrams.headOption match
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

  "UseCaseDiagramSupport" should {
    "getDefinitionFor should work" in {(td:TestData) =>
      val outputs: PassesOutput = PassesOutput()
      val pid =PathIdentifier(At(),Seq("foo"))
      val item = Domain(At(), Identifier(At(), "foo"))
      val parent = Root(Seq(item))
      outputs.refMap.add[Domain](pid, parent, item)
      val passesResult = PassesResult(PassInput.empty, outputs )
      case class TestUseCaseDiagramSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
        def makeDocLink(definition: NamedValue): String = ???
      }
      val tucds = TestUseCaseDiagramSupport(passesResult)
      tucds.getDefinitionFor[Domain](pid, parent) match {
        case Some(domain: Domain) => succeed
        case x => fail(s"Unexpected: $x")
      }
    }
  }
}
