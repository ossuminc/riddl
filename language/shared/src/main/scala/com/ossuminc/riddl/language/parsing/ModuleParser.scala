/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing production rules for Modules
  * {{{
  *   Root = Comment | Domain | Module | Author
  *   Module = Root | Context | User | Epic | Author | Application | Saga
  *   Domain = VitalDefinition | Domain | Context | User | Epic | Author | Application |  Saga
  * }}}
  */
private[parsing] trait ModuleParser {
  this: DomainParser & CommonParser =>

  private def moduleInclude[u: P]: P[Include[ModuleContents]] = {
    include[u, ModuleContents](p => moduleContents(using p))
  }

  def moduleContent[u: P]: P[ModuleContents] =
    P(domain | author | comment).asInstanceOf[P[ModuleContents]]

  def moduleContents[u: P]: P[Seq[ModuleContents]] = {
    P(moduleContent | moduleInclude[u]).asInstanceOf[P[ModuleContents]].rep(1)
  }

  def module[u: P]: P[Module] = {
    P(
      Index ~ Keywords.module ~/ identifier ~ is ~ open ~ moduleContents ~ close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Module(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
