/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*

import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait RepositoryParser {
  this: ProcessorParser & StreamingParser =>

  private def repositoryInclude[u: P]: P[Include[RepositoryContents]] = {
    include[u, RepositoryContents](repositoryDefinitions(_))
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
      location ~ Keywords.schema ~ identifier ~ is ~ schemaKind ~
        (of ~ identifier ~ as ~ typeRef).rep(1) ~
        (with_ ~ identifier ~ as ~ (typeRef ~ to ~ typeRef)).rep(0) ~
        (Keywords.index ~ on ~ fieldRef).rep(0)
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

  private def repositoryDefinitions[u: P]: P[Seq[RepositoryContents]] = {
    P(
      typeDef | schema | handler(StatementsSet.RepositoryStatements) | option |
        function | term | repositoryInclude | inlet | outlet | constant | authorRef | comment
    ).asInstanceOf[P[RepositoryContents]]./.rep(0)
  }

  private def repositoryBody[u: P]: P[Seq[RepositoryContents]] = {
    P(
      undefined(Seq.empty[RepositoryContents]) | repositoryDefinitions
    )
  }

  def repository[u: P]: P[Repository] = {
    P(
      location ~ Keywords.repository ~/ identifier ~ is ~ open ~ repositoryBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      checkForDuplicateIncludes(contents)
      Repository(loc, id, contents, brief, description)
    }
  }

}
