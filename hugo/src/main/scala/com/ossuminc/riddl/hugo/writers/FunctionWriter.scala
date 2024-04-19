package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait FunctionWriter { this: MarkdownWriter =>

  def emitFunction(function: Function, parents: Parents): Unit = {
    containerHead(function, "Function")
    h2(function.identify)
    emitVitalDefinitionDetails(function, parents)
    emitInputOutput(function.input, function.output)
    codeBlock(function.statements)
    emitUsage(function)
    emitTerms(function.terms)
  }

}
