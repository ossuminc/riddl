package com.ossuminc.riddl.analyses

import com.ossuminc.riddl.language.AST.{Domain, Identifier, Root}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.passes.{PassInfo, PassInput, PassOptions, PassesOutput}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiagramsPassTest extends AnyWordSpec with Matchers {

  "Diagrams Data" must {
    "construct ContextDiagramData" in {
      val d = Domain(At(), Identifier(At(), "domain"))
      val contextDiagramData = ContextDiagramData(d, Seq.empty, Seq.empty)
      contextDiagramData.aggregates mustBe empty
      contextDiagramData.domain mustBe d
    }
    "construct DiagramsPassOutput" in {
      val diagramsPassOutput = DiagramsPassOutput()
      diagramsPassOutput.messages mustBe empty
      diagramsPassOutput.contextDiagrams mustBe empty
      diagramsPassOutput.dataFlowDiagrams mustBe empty
    }
  }
  "DiagramsPass" must {
    "be named correctly" in {
      DiagramsPass.name mustBe "Diagrams"
    }
    "creator with empty PassesOutput yields IllegalArgumentException" in {
      val creator = DiagramsPass.creator()
      val input = PassInput(Root())
      val outputs = PassesOutput()
      val pass = intercept[IllegalArgumentException] {creator(input, outputs)}
      pass.isInstanceOf[IllegalArgumentException] mustBe true
    }
    "do something" in {
      succeed
    }
  }
}
