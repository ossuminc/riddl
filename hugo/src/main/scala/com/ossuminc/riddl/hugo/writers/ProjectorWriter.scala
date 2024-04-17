package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{OccursInProjector, Projector, ProjectorOption}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait ProjectorWriter { this: MarkdownWriter =>

  def emitProjector(projector: Projector, parents: Parents): Unit = {
    containerHead(projector, "Projector")
    emitDefDoc(projector, parents)
    list("Repository", projector.repositories)
    emitProcessorDetails[ProjectorOption, OccursInProjector](projector, parents)
  }

}
