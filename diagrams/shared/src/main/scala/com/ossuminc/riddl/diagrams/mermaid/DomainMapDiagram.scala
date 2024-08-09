package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.analyses.DomainDiagramData
import com.ossuminc.riddl.language.AST.{Context, Definition, Domain, Processor}

import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("DomainMapDiagram")
class DomainMapDiagram(domain: Domain)
    extends FlowchartDiagramGenerator(s"Map For ${domain.identify}", "TB") {

  private def nodes = domain.contents.processors ++ domain.includes.flatMap(_.contents.processors)
  private def relationships: Seq[(Processor[?], String)] = nodes.zip(List.fill(nodes.size)("contains"))

  emitDefaultClassDef()
  emitClassDefs(nodes)
  emitSubgraph(domain.identify, domain.id.value, nodes, relationships)
  emitClassAssignments(nodes)

}
