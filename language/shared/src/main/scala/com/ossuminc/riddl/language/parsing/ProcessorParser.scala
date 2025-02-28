/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait ProcessorParser
    extends VitalDefinitionParser
    with FunctionParser
    with HandlerParser
    with StreamingParser
    with CommonParser {

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
        streamlet | connector | relationship
    )./.asInstanceOf[P[OccursInProcessor]]
}
