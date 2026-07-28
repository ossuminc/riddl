/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait ProcessorParser
    extends VitalDefinitionParser
    with FunctionParser
    with HandlerParser
    with StreamingParser
    with CommonParser {

  /** An optional shape ascription usable on any processor header, between the identifier and `is`.
    * e.g. `processor P as fanout is { … }`. Recognizes the canonical shape keywords and their
    * synonyms (cascade→Flow, fanin→Merge, broadcast/fanout→Split). Wholly optional and, when the
    * `as` is absent, consumes nothing so it never collides with the other `as` uses (relationship
    * cardinality/label, repository schema data/link).
    */
  def asShape[u: P]: P[Option[StreamletShape]] =
    P(
      (as ~ StringIn(
        "void",
        "source",
        "sink",
        "flow",
        "cascade",
        "merge",
        "fanin",
        "split",
        "broadcast",
        "fanout",
        "router"
      ).!).?
    ).map(_.flatMap(kw => StreamletShape.fromKeyword(kw, At.empty)))

  /** A shared inlet/outlet declaration. Ports were historically only parsed inside streamlet
    * bodies; admitting `portlet` in `processorDefinitionContents` makes inlet/outlet declarations
    * legal in every processor body (context, entity, projector, repository, adaptor, streamlet).
    */
  def portlet[u: P]: P[Inlet | Outlet] = P(inlet | outlet)

  private def relationshipCardinality[u: P]: P[RelationshipCardinality] =
    P(StringIn("1:1", "1:N", "N:1", "N:N").!).map {
      case s: String if s == "1:1" => RelationshipCardinality.OneToOne
      case s: String if s == "1:N" => RelationshipCardinality.OneToMany
      case s: String if s == "N:1" => RelationshipCardinality.ManyToOne
      case s: String if s == "N:N" => RelationshipCardinality.ManyToMany
    }

  def relationship[u: P]: P[Relationship] =
    P(
      Index ~ Keywords.relationship ~ identifier ~/ to ~ processorRef ~ as ~ relationshipCardinality ~
        (Keywords.label ~ as ~ literalString).? ~ withMetaData ~ Index
    ).map { (start, id, procRef, cardinality, label, descriptives, end) =>
      Relationship(at(start, end), id, procRef, cardinality, label, descriptives.toContents)
    }

  def processorDefinitionContents[u: P](statementsSet: StatementsSet): P[OccursInProcessor] =
    P(
      vitalDefinitionContents | constant | invariant | function | handler(statementsSet) |
        portlet | streamlet | connector | relationship | versionDef | copyrightDef
    )./.asInstanceOf[P[OccursInProcessor]]
}
