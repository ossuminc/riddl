package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{OccursInProjector, Projector}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait ProjectorWriter { this: MarkdownWriter =>

  def emitProjector(projector: Projector, parents: Parents): Unit = {
    containerHead(projector)
    emitDefDoc(projector, parents)
    list("Repository", projector.repositories)
    emitProcessorDetails[OccursInProjector](projector, parents)
  }

}
