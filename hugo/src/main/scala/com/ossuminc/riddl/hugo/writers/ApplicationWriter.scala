package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait ApplicationWriter { this: MarkdownWriter =>

  def emitApplication(
    application: Application,
    parents: Parents
  ): Unit = {
    containerHead(application)
    emitVitalDefinitionDetails(application, parents)
    emitDefDoc(application, parents)
    for group <- application.groups do {
      h2(group.identify)
      list(group.contents.map(_.format))
    }
    emitProcessorDetails(application, parents)
  }

}
