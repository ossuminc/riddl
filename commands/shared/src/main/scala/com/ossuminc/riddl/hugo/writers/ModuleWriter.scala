/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait ModuleWriter { this: MarkdownWriter =>

  def emitModule(module: Module, parents: Parents): Unit = {
    containerHead(module)
  }
}
