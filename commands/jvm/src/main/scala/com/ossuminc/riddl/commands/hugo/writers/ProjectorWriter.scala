/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.{Parents, ProjectorContents, Projector}

trait ProjectorWriter { this: MarkdownWriter =>

  def emitProjector(projector: Projector, parents: Parents): Unit = {
    containerHead(projector)
    emitDefDoc(projector, parents)
    list("Repository", projector.repositories)
    emitProcessorDetails[ProjectorContents](projector, parents)
  }

}
