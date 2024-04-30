package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.AST.{Domain, Root, VitalDefinition}
import com.ossuminc.riddl.language.AST

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

class RootOverviewDiagram(root: Root) extends FlowchartDiagramGenerator("Root Overview", "TB", "elk") {

  private val topLevelDomains = AST.getTopLevelDomains(root).sortBy(_.id.value)

  //   protected def emitSubgraph(
  //    containingDefinition: Definition,
  //    startNodeName: String,
  //    nodes: Seq[VitalDefinition[?]],
  //    relationships: Seq[(VitalDefinition[?], String)],
  //    direction: String = "TB"

  emitDefaultClassDef()
  for { domain <- topLevelDomains } do {
    val subdomains = AST.getDomains(domain).sortBy(_.id.value)
    val subDomainRelationships = subdomains.zip(Seq.fill[String](subdomains.size)("contains"))
    val nodes = domain +: subdomains
    emitClassDefs(nodes)
    emitSubgraph(domain.identify, domain.id.value, subdomains, subDomainRelationships)
    emitClassAssignments(subdomains)
    emitDomainSubgraph(domain)
    for { subdomain <- subdomains } do {
      emitDomainSubgraph(subdomain)
    }
  }

  def emitDomainSubgraph(domain: Domain): Unit = {
    val contexts = AST.getContexts(domain)
    val applications = AST.getApplications(domain)
    val epics = AST.getEpics(domain)
    val nodes: Seq[VitalDefinition[?]] = contexts ++ applications ++ epics
    emitClassDefs(nodes)
    val contextRelationships = contexts.zip(Seq.fill[String](contexts.size)("contains"))
    emitSubgraph(name(domain, "Contexts"), domain.id.value, domain +: contexts, contextRelationships)
    val applicationRelationships = applications.zip(Seq.fill[String](applications.size)("contains"))
    emitSubgraph(name(domain, "Applications"), domain.id.value, domain +: applications, applicationRelationships)
    val epicRelationships = epics.zip(Seq.fill[String](epics.size)("contains"))
    emitSubgraph(name(domain, "Epics"), domain.id.value, domain +: epics, epicRelationships)
    emitClassAssignments(nodes)
  }
}
