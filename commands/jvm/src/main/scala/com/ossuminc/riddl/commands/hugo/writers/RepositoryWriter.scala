/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.{RepositoryContents, Repository, Parents}

trait RepositoryWriter { this: MarkdownWriter =>

  def emitSchema(repository: Repository): Unit = {
    //TODO: Schema writing
  }
  def emitRepository(
    repository: Repository,
    parents: Parents
  ): Unit = {
    containerHead(repository)
    emitDefDoc(repository, parents)
    emitSchema(repository)
    emitProcessorDetails[RepositoryContents](repository, parents)
  }

}
