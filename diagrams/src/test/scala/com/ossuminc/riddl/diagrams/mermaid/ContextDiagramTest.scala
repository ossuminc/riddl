package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.testkit.RunPassTestBase

import java.nio.file.Path

class ContextDiagramTest extends RunPassTestBase {

  "ContextDiagram" should {
    "generate a simple diagram correctly" in {
      val input = RiddlParserInput(Path.of("diagrams/src/test/input/context-relationships.riddl"))
      val result = runPassesWith(input, DiagramsPass.creator)
      val maybeDPO = result.outputOf[DiagramsPassOutput](DiagramsPass.name)
      maybeDPO match
        case Some(dpo: DiagramsPassOutput) =>
          for {
            (context, data) <- dpo.contextDiagrams
            diagram = ContextDiagram(context, data)
          } {
            val lines = diagram.generate
            info(lines.mkString("\n"))
            lines mustNot be(empty)
            val expected: Seq[String] = context.id.value match {
              case "A" =>
                """---
                  |title: Context Map For Context 'A'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef A_class color:white,stroke-width:3px;
                  |classDef B_class color:white,stroke-width:3px;
                  |subgraph Domain 'foo'
                  |  A((&nbsp;&nbsp;&nbsp;A&nbsp;&nbsp;&nbsp;))
                  |  B((&nbsp;&nbsp;&nbsp;B&nbsp;&nbsp;&nbsp;))
                  |  A-->|Sets Field 'b01' in|B((&nbsp;&nbsp;&nbsp;B&nbsp;&nbsp;&nbsp;))
                  |  A-->|Sends to Inlet 'bInlet' in|B((&nbsp;&nbsp;&nbsp;B&nbsp;&nbsp;&nbsp;))
                  |end
                  |class A A_class
                  |class B B_class
                  |""".stripMargin.split("\n").toSeq
              case "B" =>
                """---
                  |title: Context Map For Context 'B'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef B_class color:white,stroke-width:3px;
                  |subgraph Domain 'foo'
                  |  B((&nbsp;&nbsp;&nbsp;B&nbsp;&nbsp;&nbsp;))
                  |end
                  |class B B_class
                  |""".stripMargin.split("\n").toSeq
              case "C" =>
                """---
                  |title: Context Map For Context 'C'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef C_class color:white,stroke-width:3px;
                  |subgraph Domain 'foo'
                  |  C((&nbsp;&nbsp;&nbsp;C&nbsp;&nbsp;&nbsp;))
                  |end
                  |class C C_class
                  |""".stripMargin.split("\n").toSeq
              case "D" =>
                """---
                  |title: Context Map For Context 'D'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef D_class color:white,stroke-width:3px;
                  |subgraph Domain 'bar'
                  |  D((&nbsp;&nbsp;&nbsp;D&nbsp;&nbsp;&nbsp;))
                  |end
                  |class D D_class
                  |""".stripMargin.split("\n").toSeq
              case "E" =>
                """---
                  |title: Context Map For Context 'E'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef E_class color:white,stroke-width:3px;
                  |subgraph Domain 'bar'
                  |  E((&nbsp;&nbsp;&nbsp;E&nbsp;&nbsp;&nbsp;))
                  |end
                  |class E E_class
                  |""".stripMargin.split("\n").toSeq
              case "F" => 
                """---
                  |title: Context Map For Context 'F'
                  |init:
                  |    theme: dark
                  |flowchartConfig:
                  |    defaultRenderer: dagre
                  |    width: 100%
                  |---
                  |
                  |flowchart TB
                  |classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
                  |classDef F_class color:white,stroke-width:3px;
                  |subgraph Domain 'bar'
                  |  F((&nbsp;&nbsp;&nbsp;F&nbsp;&nbsp;&nbsp;))
                  |end
                  |class F F_class
                  |""".stripMargin.split("\n").toSeq
              case x   => fail(s"Unknown Context $x")
            }
            lines mustBe expected 
          }
          succeed
        case None => fail("no DiagramsPassOutput")
      end match
    }
  }
}
