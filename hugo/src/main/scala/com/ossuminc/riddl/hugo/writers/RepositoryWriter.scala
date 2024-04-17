package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{OccursInRepository, Repository, RepositoryOption}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait RepositoryWriter { this: MarkdownWriter =>

  def emitSchema(repository: Repository): Unit = {
    //TODO: Schema writing
  }
  def emitRepository(
    repository: Repository,
    parents: Parents
  ): Unit = {
    containerHead(repository, "Repository")
    emitDefDoc(repository, parents)
    emitSchema(repository)
    emitProcessorDetails[RepositoryOption, OccursInRepository](repository, parents)
  }

}
