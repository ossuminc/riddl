/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.diagrams.mermaid.DomainMapDiagram

trait DomainWriter { this: MarkdownWriter =>

  def emitDomain(domain: Domain, parents: Parents): Unit = {
    val diagram = DomainMapDiagram(domain)

    containerHead(domain)
    emitVitalDefinitionDetails(domain, parents)
    h2("Domain Map")
    emitMermaidDiagram(diagram.generate)
    emitTypes(domain.types, domain +: parents)
    emitAuthorInfo(domain.authors)
    definitionToc("Subdomains", domain.domains)
    definitionToc("Contexts", domain.contexts)
    definitionToc("Applications", domain.applications)
    definitionToc("Epics", domain.epics)
  }


}
