/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*

import fastparse.*
import fastparse.ScalaWhitespace.*
import Terminals.*

private[parsing] trait RepositoryParser {

  this: HandlerParser
    with ReferenceParser
    with StatementParser
    with StreamingParser
    with FunctionParser
    with TypeParser =>

  private def repositoryOptions[u: P]: P[Seq[RepositoryOption]] = {
    options[u, RepositoryOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        RepositoryTechnologyOption(loc, args)
    }
  }

  private def repositoryInclude[x: P]: P[Include[RepositoryDefinition]] = {
    include[RepositoryDefinition, x](repositoryDefinitions(_))
  }

  private def repositoryDefinitions[u: P]: P[Seq[RepositoryDefinition]] = {
    P(
      typeDef | handler(StatementsSet.RepositoryStatements) |
        function | term | repositoryInclude | inlet | outlet | constant 
    ).rep(0)
  }

  def repository[u: P]: P[Repository] = {
    P(
      location ~ Keywords.repository ~/ identifier ~ authorRefs ~ is ~ open ~
        repositoryOptions ~
        (undefined(Seq.empty[RepositoryDefinition]) | repositoryDefinitions) ~
        close ~ briefly ~ description
    ).map { case (loc, id, authors, options, defs, brief, description) =>
      val groups = defs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val constants = mapTo[Constant](groups.get(classOf[Constant]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[RepositoryDefinition]](
        groups.get(
          classOf[Include[RepositoryDefinition]]
        )
      )

      Repository(
        loc,
        id,
        types,
        handlers,
        inlets,
        outlets,
        authors,
        functions,
        constants,
        invariants,
        includes,
        options,
        terms,
        brief,
        description
      )
    }
  }

}
