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

/** The deprecated `nebula` entry point: a whole-input, anonymous, unwrapped sequence of any
  * top-level definitions.
  *
  * [[AST.Module]] subsumes it — a Module has exactly this content model but is named, carries
  * metadata, and is embeddable. So this production still parses, emits a single `[deprecated]`
  * message, and yields a [[AST.Module]] with the synthetic id [[AST.Module.syntheticId]].
  */
private[parsing] trait NebulaParser {
  this: ProcessorParser & DomainParser & AdaptorParser & ContextParser & EntityParser & EpicParser &
    FunctionParser & HandlerParser & ModuleParser & ProjectorParser & RepositoryParser &
    RootParser & SagaParser & StreamingParser & TypeParser & Readability & CommonParser =>

  private def nebulaContents[u: P]: P[Seq[ModuleContents]] = P(moduleContent).rep(0)

  def nebula[u: P]: P[Module] = {
    P(
      Start ~ Index ~ nebulaContents ~ Index ~ End
    ).map { case (start: Int, contents: Seq[ModuleContents], end: Int) =>
      val loc = at(start, end)
      deprecation(
        loc,
        "an anonymous 'nebula' of definitions is deprecated; use 'module <id> is { ... }'",
        code = Option(RuleId.AnonymousNebula),
        autoFixable = false
      )
      Module.anonymous(loc, contents.toContents)
    }
  }
}
