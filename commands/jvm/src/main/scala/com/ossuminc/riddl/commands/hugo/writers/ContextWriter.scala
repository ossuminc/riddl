/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.ContextMapDiagram
import com.ossuminc.riddl.language.AST.{Context, ContextContents, Definition, Parents}

trait ContextWriter { this: MarkdownWriter =>

  private def emitContextMap(context: Context, diagram: Option[ContextMapDiagram]): Unit = {
    if diagram.nonEmpty then
      h2("Context Map")
      val lines = diagram.get.generate
      emitMermaidDiagram(lines)
    end if
  }

  def emitContext(context: Context, parents: Parents): Unit = {
    containerHead(context)
    val maybeDiagram = generator.diagrams.contextDiagrams.get(context).map(data => ContextMapDiagram(context, data))
    emitVitalDefinitionDetails(context, parents)
    emitContextMap(context, maybeDiagram)
    emitOptions(context.options)
    definitionToc("Entities", context.entities)
    definitionToc("Adaptors", context.adaptors)
    definitionToc("Sagas", context.sagas)
    definitionToc("Streamlets", context.streamlets)
    list("Connectors", context.connectors)
    emitProcessorDetails[ContextContents](context, parents)
    // TODO: generate a diagram for the processors and pipes
  }

}
