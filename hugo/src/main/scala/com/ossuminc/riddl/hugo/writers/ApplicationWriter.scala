package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait ApplicationWriter { this: MarkdownWriter =>

  def emitApplication(
    application: Application,
    parents: Parents
  ): Unit = {
    containerHead(application, "Application")
    emitVitalDefinitionDetails(application, parents)
    emitDefDoc(application, parents)
    for group <- application.groups do {
      h2(group.identify)
      list(group.elements.map(_.format))
    }
    emitProcessorDetails(application, parents)
  }

}
