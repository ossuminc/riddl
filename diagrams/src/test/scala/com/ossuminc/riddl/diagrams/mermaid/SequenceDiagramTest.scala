package com.ossuminc.riddl.diagrams.mermaid
import com.ossuminc.riddl.language.AST.{Definition, RootContainer}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.testkit.ValidatingTest

class SequenceDiagramTest extends ValidatingTest {

  case class SDS(passesResult: PassesResult) extends SequenceDiagramSupport {
    def makeDocLink(definition: Definition): String = {
      s"https://example.com/${definition.kind}/${definition.id.value}"
    }
  }

  "SequenceDiagram" must {
    "handle a simple use case" in {
      val input = RiddlParserInput(
        """
          |domain Marvel is {
          |  command DonTheSuit { ??? }
          |  user Robert_Downey_Jr is "Iron Man"
          |  application Edwin_Jarvis is {
          |    group nada is {
          |      button DoItAll acquires DonTheSuit
          |    }
          |  }
          |  epic GetSuitedUp is {
          |    case DonTheSuite is {
          |      user Robert_Downey_Jr wants to "put on his Iron Man suit" so that "he can play his Iron Man scene"
          |      step from user Marvel.Robert_Downey_Jr "pushes" button Edwin_Jarvis.nada.DoItAll
          |   }
          |  }
          |}
          |""".stripMargin
      )
      parseValidateAndThen(input, CommonOptions.noWarnings) {
        case (result: PassesResult, root: RootContainer, rpi: RiddlParserInput, message: Messages) =>
          val domain = root.domains.head
          val epic = domain.epics.head
          val useCase = epic.cases.head
          val sd = SequenceDiagram(new SDS(result), useCase)
          val diagram = sd.generate
          println(diagram.mkString("\n"))
          diagram mustNot be(empty)
      }
    }
  }
}
