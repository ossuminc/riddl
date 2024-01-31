package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase

import java.nio.file.Path

class RootOverviewDiagramTest extends RunPassTestBase {

  "ContextDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("diagrams/src/test/input/context-relationships.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator)
      val diagram = RootOverviewDiagram(result.root)
      val lines = diagram.generate
      lines mustNot be(empty)
      info(lines.mkString("\n"))
      val expected: Seq[String] =
        """---
          |title: Root Overview
          |init:
          |    theme: dark
          |flowchart:
          |    defaultRenderer: dagre
          |    width: 100%
          |    useMaxWidth: true
          |    securityLevel: loose
          |---
          |
          |flowchart TD
          |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
          |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue; 
          |  classDef B_class fill:white,stroke:#333,stroke-width:3px,color:green; 
          |  classDef C_class fill:white,stroke:#333,stroke-width:3px,color:purple; 
          |  subgraph 'Domain 'foo''
          |    direction TB
          |    foo((Domain 'foo'))
          |    A((fa:fa-house<br/>Context 'A'))
          |    B((fa:fa-house<br/>Context 'B'))
          |    C((fa:fa-house<br/>Context 'C'))
          |    foo-->|contains|A((fa:fa-house<br/>Context 'A'))
          |    foo-->|contains|B((fa:fa-house<br/>Context 'B'))
          |    foo-->|contains|C((fa:fa-house<br/>Context 'C'))
          |  end
          |  class A A_class
          |  class B B_class
          |  class C C_class
          |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
          |  classDef D_class fill:white,stroke:#333,stroke-width:3px,color:red; 
          |  subgraph 'Domain 'bar''
          |    direction TB
          |    bar((Domain 'bar'))
          |    D((fa:fa-house<br/>Context 'D'))
          |    bar-->|contains|D((fa:fa-house<br/>Context 'D'))
          |  end
          |  class D D_class
          |""".stripMargin.split('\n').toSeq
      lines mustBe expected
    }
  }
}
