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
  this: ProcessorParser & StreamingParser & Readability =>

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

  private def data[u: P]: P[(Identifier, TypeRef)] = {
    P(of ~ identifier ~ as ~ typeRef)./
  }

  private def link[u: P]: P[(Identifier, FieldRef, FieldRef)] =
    P(Keywords.link ~ identifier ~ as ~ fieldRef ~ to ~ fieldRef)./

  private def index[u: P]: P[FieldRef] =
    P(Keywords.index ~ Keywords.on ~ fieldRef)./

  def schema[u: P]: P[Schema] = {
    P(
      location ~ Keywords.schema ~ identifier ~ is ~ schemaKind ~
        data.rep(1) ~ link.rep(0) ~ index.rep(0) ~ withDescriptives
    )./.map { case (at, id, kind, data, links, indices, descriptives) =>
      val dataMap = Map.from[Identifier, TypeRef](data)
      val linkMap = Map.from[Identifier, (FieldRef, FieldRef)](links.map(i => i._1 -> (i._2 -> i._3)))
      Schema(at, id, kind, dataMap, linkMap, indices, descriptives)
    }
  }

  private def repositoryDefinitions[u: P]: P[Seq[RepositoryContents]] = {
    P(
      processorDefinitionContents(StatementsSet.RepositoryStatements) | schema | repositoryInclude
    ).asInstanceOf[P[RepositoryContents]]./.rep(0)
  }

  private def repositoryBody[u: P]: P[Seq[RepositoryContents]] = {
    P(
      undefined(Seq.empty[RepositoryContents]) | repositoryDefinitions
    )
  }

  def repository[u: P]: P[Repository] = {
    P(
      location ~ Keywords.repository ~/ identifier ~ is ~ open ~ repositoryBody ~ close ~ withDescriptives
    ).map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Repository(loc, id, contents, descriptives)
    }
  }

}
