/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.{Context, Definition, Domain, Processor}
import com.ossuminc.riddl.passes.diagrams.DomainDiagramData

import scala.scalajs.js.annotation.*

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
