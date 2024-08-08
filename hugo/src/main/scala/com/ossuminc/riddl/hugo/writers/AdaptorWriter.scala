package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{Adaptor, AdaptorContents, Parents}

trait AdaptorWriter { this: MarkdownWriter =>

  def emitAdaptor(adaptor: Adaptor, parents: Parents): Unit = {
    containerHead(adaptor)
    emitVitalDefinitionDetails(adaptor, parents)
    h2(s"Direction: ${adaptor.direction.format} ${adaptor.context.format}")
    emitProcessorDetails[AdaptorContents](adaptor, parents)
  }
}
