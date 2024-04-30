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
    & ReferenceParser
    & StatementParser
    & StreamingParser
    & FunctionParser
    & TypeParser =>

  private def repositoryInclude[u: P]: P[IncludeHolder[OccursInRepository]] = {
    include[u, OccursInRepository](repositoryDefinitions(_))
  }

  private def schemaKind[u: P]: P[RepositorySchemaKind] = {
    P(
      StringIn(
        "flat",
        "relational",
        "time-series",
        "graphical",
        "hierarchical",
        "star",
        "document",
        "columnar",
        "vector",
        "other"
      ).!.map {
        case "flat"         => RepositorySchemaKind.Flat
        case "relational"   => RepositorySchemaKind.Relational
        case "time-series"  => RepositorySchemaKind.TimeSeries
        case "graphical"    => RepositorySchemaKind.Graphical
        case "hierarchical" => RepositorySchemaKind.Hierarchical
        case "star"         => RepositorySchemaKind.Star
        case "document"     => RepositorySchemaKind.Document
        case "columnar"     => RepositorySchemaKind.Columnar
        case "vector"       => RepositorySchemaKind.Vector
        case _              => RepositorySchemaKind.Other
      }
    )
  }

  private def schema[u: P]: P[Schema] = {
    P(
      location ~ Keywords.schema ~ identifier ~ Readability.is ~ schemaKind ~
        (Readability.of ~ identifier ~ Readability.as ~ typeRef).rep(1) ~
        (Readability.with_ ~ identifier ~ Readability.as ~ (typeRef ~ Readability.to ~ typeRef)).rep(0) ~
        (Keywords.index ~ Readability.on ~ fieldRef).rep(0)
    ).map { case (at, id, kind, records, relations, indices) =>
      Schema(
        at,
        id,
        kind,
        Map.from[Identifier, TypeRef](records),
        Map.from[Identifier, (TypeRef, TypeRef)](relations),
        indices
      )
    }
  }

  private def repositoryDefinitions[u: P]: P[Seq[OccursInRepository]] = {
    P(
      typeDef | schema | handler(StatementsSet.RepositoryStatements) | option |
        function | term | repositoryInclude | inlet | outlet | constant | authorRef | comment
    ).rep(0)
  }

  private def repositoryBody[u: P]: P[Seq[OccursInRepository]] = {
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
