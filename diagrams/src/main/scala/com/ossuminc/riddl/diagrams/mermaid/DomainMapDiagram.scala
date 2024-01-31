package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.diagrams.DomainDiagramData
import com.ossuminc.riddl.language.AST.{Definition, Context, Domain, Processor}

class DomainMapDiagram(domain: Domain)
    extends FlowchartDiagramGenerator(s"Map For ${domain.identify}", "TB") {
  
  private def nodes = domain.contents.processors ++ domain.includes.flatMap(_.contents.processors)
  private def relationships: Seq[(Processor[_, _], String)] = nodes.zip(List.fill(nodes.size)("contains"))

  emitDefaultClassDef()
  emitClassDefs(nodes)
  emitSubgraph(domain, domain.id.value, nodes, relationships)
  emitClassAssignments(nodes)

}
