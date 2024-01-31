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

  private val topLevelDomains = root.domains ++ root.includes.filter[Domain]
  for { domain <- topLevelDomains } do {
    val nodes: Seq[Definition] = domain.domains ++ domain.contexts ++ domain.applications ++ domain.epics
    val relationships = nodes.zip(Seq.fill[String](nodes.size)("contains"))
    emitDefaultClassDef()
    emitClassDefs(nodes)
    emitSubgraph(domain, domain.id.value, domain +: nodes, relationships)
    emitClassAssignments(nodes)
  }

}
