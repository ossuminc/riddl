/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.language.AST.{Context, Definition, Processor}
import com.ossuminc.riddl.passes.diagrams.ContextDiagramData

import scalajs.js.annotation.*

/** Context Diagram generator using a DataFlow Diagram from Mermaid
  *
  * @param context
  *   The referent relevant to this diagram
  * @param data
  *   The data collected by the ((Diagrams Pass)) for this diagram.
  */
@JSExportTopLevel("ContextMapDiagram")
case class ContextMapDiagram(context: Context, data: ContextDiagramData)
    extends FlowchartDiagramGenerator(s"Context Map For ${context.identify}", "TB") {

  private def relatedContexts = data.relationships.map(_._1).distinct
  private def nodes: Seq[Processor[?]] = context +: relatedContexts
  private def relationships: Seq[(Processor[?], String)] = data.relationships

  emitDefaultClassDef()
  emitClassDefs(nodes)
  emitSubgraph(data.domain.identify, context.id.value, nodes, relationships)
  emitClassAssignments(nodes)
}
