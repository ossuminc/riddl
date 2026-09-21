/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}

import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait RepositoryParser {
  this: ProcessorParser & StreamingParser & Readability =>

  private def repositoryInclude[u: P]: P[Include[RepositoryContents]] = {
    include[u, RepositoryContents]((p: P[?]) => repositoryDefinitions(using p.asInstanceOf[P[u]]))
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

  /** `of <name> as <typeRef> [with history]` (B3, 2026-09-21). The `NoCut` is load-bearing:
    * `Keywords.with` cuts after the keyword, and the schema's OWN `with { … }` follows the last
    * data line -- without it, `with {` would fail inside this option instead of backtracking to
    * `withMetaData` (the same idiom `refOrLookup` needs for `at`).
    */
  private def data[u: P]: P[(Identifier, TypeRef, Boolean)] = {
    P(of ~ identifier ~ as ~ typeRef ~ NoCut(Keywords.`with` ~ Keywords.history).!.?)./.map {
      case (id, tr, withHistory) => (id, tr, withHistory.nonEmpty)
    }
  }

  private def link[u: P]: P[(Identifier, FieldRef, FieldRef)] =
    P(Keywords.link ~ identifier ~ as ~ fieldRef ~ to ~ fieldRef)./

  private def index[u: P]: P[FieldRef] =
    P(Keywords.index ~ Keywords.on ~ fieldRef)./

  /** B3: `key on field F` -- a UNIQUE natural key; `index on` stays an index. */
  private def key[u: P]: P[FieldRef] =
    P(Keywords.key ~ Keywords.on ~ fieldRef)./

  def schema[u: P]: P[Schema] = {
    P(
      Index ~ Keywords.schema ~ identifier ~ is ~ schemaKind ~
        data.rep(1) ~ link.rep(0) ~ key.rep(0) ~ index.rep(0) ~ withMetaData ~ Index
    )./.map { case (start, id, kind, data, links, keys, indices, descriptives, end) =>
      val dataMap = Map.from[Identifier, TypeRef](data.map(d => d._1 -> d._2))
      val history = data.collect { case (id, _, true) => id }
      val linkMap =
        Map.from[Identifier, (FieldRef, FieldRef)](links.map(i => i._1 -> (i._2 -> i._3)))
      Schema(
        at(start, end), id, kind, dataMap, linkMap, indices, descriptives.toContents, keys, history
      )
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
      Index ~ Keywords.repository ~/ identifier ~ asShape ~ is ~ open ~ repositoryBody ~ close ~
        withMetaData ~ Index
    ).map { case (start, id, ascribed, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Repository(
        at(start, end),
        id,
        contents.toContents,
        ascribedShape = ascribed,
        metadata = descriptives.toContents
      )
    }
  }

}
