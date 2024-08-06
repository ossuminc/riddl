package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.analyses.DiagramsPass
import com.ossuminc.riddl.hugo.mermaid.RootOverviewDiagram
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase

import java.nio.file.Path

class RootOverviewDiagramTest extends RunPassTestBase {

  "ContextDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("hugo/src/test/input/context-relationships.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator())
      val diagram = RootOverviewDiagram(result.root)
      val lines = diagram.generate
      lines mustNot be(empty)
      val actual = lines.mkString("\n")
      info(actual)
      val expected: String =
        """---
          |title: Root Overview
          |init:
          |    theme: dark
          |flowchart:
          |    defaultRenderer: elk
          |    width: 100%
          |    useMaxWidth: true
          |    securityLevel: loose
          |---
          |
          |flowchart TB
          |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
          |  classDef bar_class color:white,stroke-width:3px;
          |  subgraph 'Domain 'bar''
          |    direction TB
          |  end
          |  classDef D_class fill:white,stroke:#333,stroke-width:3px,color:red;
          |  subgraph 'bar-Contexts'
          |    direction TB
          |    bar((Domain 'bar'))
          |    D((fa:fa-house<br/>Context 'D'))
          |    bar-->|contains|D((fa:fa-house<br/>Context 'D'))
          |  end
          |  subgraph 'bar-Applications'
          |    direction TB
          |    bar((Domain 'bar'))
          |  end
          |  subgraph 'bar-Epics'
          |    direction TB
          |    bar((Domain 'bar'))
          |  end
          |  class D D_class
          |  classDef foo_class color:white,stroke-width:3px;
          |  subgraph 'Domain 'foo''
          |    direction TB
          |  end
          |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue;
          |  classDef B_class fill:white,stroke:#333,stroke-width:3px,color:green;
          |  classDef C_class fill:white,stroke:#333,stroke-width:3px,color:purple;
          |  subgraph 'foo-Contexts'
          |    direction TB
          |    foo((Domain 'foo'))
          |    A((fa:fa-house<br/>Context 'A'))
          |    B((fa:fa-house<br/>Context 'B'))
          |    C((fa:fa-house<br/>Context 'C'))
          |    foo-->|contains|A((fa:fa-house<br/>Context 'A'))
          |    foo-->|contains|B((fa:fa-house<br/>Context 'B'))
          |    foo-->|contains|C((fa:fa-house<br/>Context 'C'))
          |  end
          |  subgraph 'foo-Applications'
          |    direction TB
          |    foo((Domain 'foo'))
          |  end
          |  subgraph 'foo-Epics'
          |    direction TB
          |    foo((Domain 'foo'))
          |  end
          |  class A A_class
          |  class B B_class
          |  class C C_class""".stripMargin

      actual mustBe expected
    }
  }
}
