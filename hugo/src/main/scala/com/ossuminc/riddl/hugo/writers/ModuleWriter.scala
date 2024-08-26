package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait ModuleWriter { this: MarkdownWriter =>

  def emitModule(module: Module, parents: Parents): Unit = {
    containerHead(module)
  }
}
