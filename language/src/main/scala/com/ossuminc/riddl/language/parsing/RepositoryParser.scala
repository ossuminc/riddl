/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*

import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

private[parsing] trait RepositoryParser {

  this: HandlerParser
    with ReferenceParser
    with StatementParser
    with StreamingParser
    with FunctionParser
    with TypeParser =>

  private def repositoryOption[u: P]: P[RepositoryOption] = {
    option[u, RepositoryOption](RiddlOptions.repositoryOptions) {
      case (loc, RiddlOption.technology, args) => RepositoryTechnologyOption(loc, args)
      case (loc, RiddlOption.kind, args)       => RepositoryKindOption(loc, args)
      case (loc, RiddlOption.css, args)      => RepositoryCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => RepositoryIconOption(loc, args)
    }
  }

  private def repositoryInclude[x: P]: P[IncludeHolder[OccursInRepository]] = {
    include[OccursInRepository, x](repositoryDefinitions(_))
  }

  private def repositoryDefinitions[u: P]: P[Seq[OccursInRepository]] = {
    P(
      typeDef | handler(StatementsSet.RepositoryStatements) | repositoryOption |
        function | term | repositoryInclude | inlet | outlet | constant | authorRef | comment
    ).rep(0)
  }
  
  private def repositoryBody[u:P]: P[Seq[OccursInRepository]] = {
    P(
      undefined(Seq.empty[OccursInRepository]) | repositoryDefinitions
    )
  }

  def repository[u: P]: P[Repository] = {
    P(
      location ~ Keywords.repository ~/ identifier ~ is ~ open ~ repositoryBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      val mergedContent = mergeAsynchContent[OccursInRepository](contents)
      Repository(loc, id, mergedContent, brief, description)
    }
  }

}
