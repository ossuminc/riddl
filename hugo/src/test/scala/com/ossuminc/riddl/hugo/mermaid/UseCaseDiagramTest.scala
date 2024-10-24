package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.AST.{Contents, Definition, Domain, Identifier, PathIdentifier, Root}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{pc,ec}
import com.ossuminc.riddl.passes.{AbstractRunPassTest, PassInput, PassesOutput, PassesResult}
import com.ossuminc.riddl.diagrams.mermaid.{UseCaseDiagram, UseCaseDiagramSupport}
import com.ossuminc.riddl.passes.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.utils.URL
import org.scalatest.TestData

import java.nio.file.Path
import java.net.URI
import scala.reflect.ClassTag
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UseCaseDiagramTest extends AbstractRunPassTest {

  case class TestUCDSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
    private val symTab = passesResult.symbols
    def makeDocLink(namedValue: Definition): String = {
      val parents = symTab.parentsOf(namedValue)
      val uri = new URI(
        "http",
        "example.com",
        parents.map(_.id.value).mkString("//", "/", "/") + s"${namedValue.id.value}",
        null,
        null
      )
      uri.toASCIIString
    }
  }

  "UseCaseDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val url = URL.fromCwdPath("hugo/src/test/input/epic.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        val result = runPassesWith(rpi, DiagramsPass.creator())
        val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
        maybeDPO match
          case Some(dpo: DiagramsPassOutput) =>
            dpo.useCaseDiagrams.headOption match
              case Some((uc, useCaseDiagramData)) =>
                val useCaseDiagram = UseCaseDiagram(TestUCDSupport(result), useCaseDiagramData)
                val lines = useCaseDiagram.generate
                // info(lines.mkString("\n"))
                lines mustNot be(empty)
                succeed
              case None => fail("No use cases or epic")
            end match
          case None => fail("no DiagramsPassOutput")
        end match
      }
      Await.result(future, 10.seconds)
    }
  }

  "UseCaseDiagramSupport" should {
    "getDefinitionFor should work" in { (td: TestData) =>
      val outputs: PassesOutput = PassesOutput()
      val pid = PathIdentifier(At(), Seq("foo"))
      val item = Domain(At(), Identifier(At(), "foo"))
      val parent = Root(Contents(item))
      outputs.refMap.add[Domain](pid, parent, item)
      val passesResult = PassesResult(PassInput.empty, outputs)
      case class TestUseCaseDiagramSupport(passesResult: PassesResult) extends UseCaseDiagramSupport {
        def makeDocLink(definition: Definition): String = ???
      }
      val tucds = TestUseCaseDiagramSupport(passesResult)
      tucds.getDefinitionFor[Domain](pid, parent) match {
        case Some(domain: Domain) => succeed
        case x                    => fail(s"Unexpected: $x")
      }
    }
  }
}
