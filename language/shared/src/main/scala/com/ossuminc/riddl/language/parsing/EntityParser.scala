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
      location ~ Keywords.state ~ identifier ~/ (of | is) ~ typeRef ~/ withDescriptives
    )./.map { case (loc, id, typRef, descriptives) =>
      State(loc, id, typRef, descriptives)
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
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/ entityBody ~ close ~ withDescriptives
    )./ map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Entity(loc, id, contents, descriptives)
    }
  }
}
