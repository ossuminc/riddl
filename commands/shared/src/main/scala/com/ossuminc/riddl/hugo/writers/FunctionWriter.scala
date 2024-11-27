/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait FunctionWriter { this: MarkdownWriter =>

  def emitFunction(function: Function, parents: Parents): Unit = {
    containerHead(function)
    h2(function.identify)
    emitVitalDefinitionDetails(function, parents)
    emitInputOutput(function.input, function.output)
    codeBlock(function.statements)
    emitTerms(function.terms)
  }

}
