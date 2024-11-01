/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser {
  this: ProcessorParser & StreamingParser =>

  def state[u: P]: P[State] = {
    P(
      Index ~ Keywords.state ~ identifier ~/ (of | is) ~ typeRef ~/ withMetaData ~ Index
    )./.map { case (start, id, typRef, descriptives, end) =>
      State(at(start,end), id, typRef, descriptives.toContents)
    }
  }

  private def entityInclude[u: P]: P[Include[EntityContents]] = {
    include[u, EntityContents](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[EntityContents]] = {
    P(
      processorDefinitionContents(StatementsSet.EntityStatements) | state | entityInclude
    ).asInstanceOf[P[EntityContents]]./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[EntityContents]] = {
    P(
      undefined(Seq.empty[EntityContents])./ | entityDefinitions./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      Index ~ Keywords.entity ~/ identifier ~ is ~ open ~/ entityBody ~ close ~ withMetaData ~ Index
    )./ map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Entity(at(start,end), id, contents.toContents, descriptives.toContents)
    }
  }
}
