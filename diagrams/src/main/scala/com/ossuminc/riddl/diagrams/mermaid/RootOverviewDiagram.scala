package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.{Definition, Domain, Root}

object RootOverviewDiagram {
  private val containerStyles: Seq[String] = Seq(
    "font-size:1pc,fill:#000088,stroke:black,stroke-width:6,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#2222AA,stroke:black,stroke-width:5,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#4444CC,stroke:black,stroke-width:4,border:solid,color:white,margin-top:36px",
    "font-size:1pc,fill:#6666EE,stroke:black,stroke-width:3,border:solid,color:black,margin-top:36px",
    "font-size:1pc,fill:#8888FF,stroke:black,stroke-width:2,border:solid,color:black,margin-top:36px",
    "font-size:1pc,fill:#AAAAFF,stroke:black,stroke-width:1,border:solid,color:black,margin-top:36px"
  )
}


class RootOverviewDiagram(root: Root) extends FlowchartDiagramGenerator("Root Overview", "TD") {

  private val domains = root.domains ++ root.includes.filter[Domain]

  append(openBox(root))


  def openBox(definition: Definition, level: Int = 0): String = {
    val contents: Seq[Definition] = {
      definition match {
        case r: Root => r.domains ++ r.includes.filter[Domain]
        case d: Domain => d.domains ++ d.includes.filter[Domain]
        case _ => Seq.empty[Definition]
      }
    }
    val mid = contents.foldLeft("") { case (s, c) => s + openBox(c, level + 1) }
    if !definition.isImplicit then {
      val technology = getTechnology(definition)
      val name = definition.id.value
      val head = "  ".repeat(level) +
        s"subgraph $name [\"$name<br/><small>${definition.briefValue}<br/>($technology)</small>\"]\n"
      head + mid + "  ".repeat(level) + "end\n" + "  ".repeat(level) +
        s"style $name ${RootOverviewDiagram.containerStyles(level)}\n"
    } else {
      mid
    }
  }


  /*
  graph TB
    linkStyle default fill:#ffffff

  1["<div style='font-weight: bold'>User</div><div style='font-size: 70%; margin-top: 0px'>[Person]</div><div " +
    "style='font-size: 80%; margin-top:10px'>A user of my software system.</div>"]
  style 1 fill:#08427b,stroke:#052e56,color:#ffffff
  2["<div style='font-weight: bold'>Software System</div><div style='font-size: 70%; margin-top: 0px'>[Software " +
    "System]</div><div style='font-size: 80%; margin-top:10px'>My software system.</div>"]
  style 2 fill:#1168bd,stroke:#0b4884,color:#ffffff

   */

}
