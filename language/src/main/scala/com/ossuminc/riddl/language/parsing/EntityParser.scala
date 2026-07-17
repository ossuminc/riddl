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

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser {
  this: ProcessorParser & StreamingParser =>

  private def stateContent[u: P]: P[StateContents] =
    P(handler(StatementsSet.EntityStatements) | comment).asInstanceOf[P[StateContents]]

  private def stateContents[u: P]: P[Seq[StateContents]] =
    stateContent.rep(1)

  private def stateBody[u: P]: P[Seq[StateContents]] =
    P(is ~ open ~ (undefined(Seq.empty[StateContents]) | stateContents) ~ close)

  def state[u: P]: P[State] = {
    P(
      Index ~ Keywords.state ~ identifier ~/ (of | is) ~ typeRef ~/
        stateBody.? ~ withMetaData ~ Index
    )./.map { case (start, id, typRef, body, descriptives, end) =>
      State(at(start, end), id, typRef,
        body.getOrElse(Seq.empty).toContents,
        descriptives.toContents)
    }
  }

  private def entityInclude[u: P]: P[Include[EntityContents]] = {
    include[u, EntityContents]((p: P[?]) => entityDefinitions(using p.asInstanceOf[P[u]]))
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
    )./ map { case (start, id, contents, meta, end) =>
      checkForDuplicateIncludes(contents)
      Entity(at(start,end), id, contents.toContents, meta.toContents)
    }
  }
}
