package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase

import java.nio.file.Path

class ContextMapDiagramTest extends RunPassTestBase {

  "ContextDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("diagrams/src/test/input/context-relationships.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator)
      val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
      maybeDPO match
        case Some(dpo: DiagramsPassOutput) =>
          var failures = Seq.empty[String]  
          for {
            (context, data) <- dpo.contextDiagrams
            diagram = ContextMapDiagram(context, data)
          } {
            val lines = diagram.generate
            lines mustNot be(empty)
            val expected: Seq[String] = context.id.value match {
              case "A" =>
                """---
                  |title: Context Map For Context 'A'
                  |init:
                  |    theme: dark
                  |flowchart:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |    useMaxWidth: true
                  |    securityLevel: loose
                  |---
                  |
                  |flowchart TB
                  |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue; 
                  |  classDef B_class fill:white,stroke:#333,stroke-width:3px,color:green; 
                  |  classDef C_class fill:white,stroke:#333,stroke-width:3px,color:purple; 
                  |  subgraph 'Domain 'foo''
                  |    direction TB
                  |    A((fa:fa-house<br/>Context 'A'))
                  |    B((fa:fa-house<br/>Context 'B'))
                  |    C((fa:fa-house<br/>Context 'C'))
                  |    A-->|Sets Field 'b01' in|B((fa:fa-house<br/>Context 'B'))
                  |    A-->|Sends to Inlet 'bInlet' in|B((fa:fa-house<br/>Context 'B'))
                  |    A-->|Uses Command 'CCommand' from|C((fa:fa-house<br/>Context 'C'))
                  |    A-->|References Entity 'CEntity' in|C((fa:fa-house<br/>Context 'C'))
                  |  end
                  |  class A A_class
                  |  class B B_class
                  |  class C C_class
                  |""".stripMargin.split("\n").toSeq
              case "B" =>
                """---
                  |title: Context Map For Context 'B'
                  |init:
                  |    theme: dark
                  |flowchart:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |    useMaxWidth: true
                  |    securityLevel: loose
                  |---
                  |
                  |flowchart TB
                  |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |  classDef B_class fill:white,stroke:#333,stroke-width:3px,color:green; 
                  |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue; 
                  |  subgraph 'Domain 'foo''
                  |    direction TB
                  |    B((fa:fa-house<br/>Context 'B'))
                  |    A((fa:fa-house<br/>Context 'A'))
                  |    B-->|Uses Type 'AEvents' from|A((fa:fa-house<br/>Context 'A'))
                  |    B-->|Uses Type 'AEvents' from|A((fa:fa-house<br/>Context 'A'))
                  |  end
                  |  class B B_class
                  |  class A A_class
                  |""".stripMargin.split("\n").toSeq
              case "C" =>
                """---
                  |title: Context Map For Context 'C'
                  |init:
                  |    theme: dark
                  |flowchart:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |    useMaxWidth: true
                  |    securityLevel: loose
                  |---
                  |
                  |flowchart TB
                  |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |  classDef C_class fill:white,stroke:#333,stroke-width:3px,color:purple; 
                  |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue; 
                  |  subgraph 'Domain 'foo''
                  |    direction TB
                  |    C((fa:fa-house<br/>Context 'C'))
                  |    A((fa:fa-house<br/>Context 'A'))
                  |    C-->|Sends to Inlet 'aInlet' in|A((fa:fa-house<br/>Context 'A'))
                  |  end
                  |  class C C_class
                  |  class A A_class
                  |""".stripMargin.split("\n").toSeq
              case "D" =>
                """---
                  |title: Context Map For Context 'D'
                  |init:
                  |    theme: dark
                  |flowchart:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |    useMaxWidth: true
                  |    securityLevel: loose
                  |---
                  |
                  |flowchart TB
                  |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |  classDef D_class fill:white,stroke:#333,stroke-width:3px,color:red; 
                  |  subgraph 'Domain 'bar''
                  |    direction TB
                  |    D((fa:fa-house<br/>Context 'D'))
                  |  end
                  |  class D D_class
                  |""".stripMargin.split("\n").toSeq
              case x   => fail(s"Unknown Context $x")
            }
            if lines != expected then
              val failure = s"${context.id.value} failed "
              info(failure)
              info(lines.mkString("\n"))
              failures = failures :+ failure  
            end if  
            // lines mustBe expected 
          }
          if failures.nonEmpty then 
            fail(s"Failures detected in generator output: ${failures.mkString("\n  ", "\n  ", "\n")}")
          else  
            succeed
        case None => fail("no DiagramsPassOutput")
      end match
    }
  }
}
