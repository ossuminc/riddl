/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait ModuleWriter { this: MarkdownWriter =>

  def emitModule(module: Module, parents: Parents): Unit = {
    containerHead(module)
  }
}
