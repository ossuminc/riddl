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

/** Parsing production rules for Modules.
  *
  * A Module is a FLAT collection: any top-level definition may appear directly inside it, in any
  * order, with no hierarchy enforced at that level. (The internal rules of each contained
  * definition still apply.)
  * {{{
  *   Module = { BASTImport | Adaptor | Author | Comment | Connector | Constant | Context |
  *              Domain | Entity | Epic | Function | Invariant | Module | Projector |
  *              Relationship | Repository | Saga | Streamlet | Type | User | Include }
  * }}}
  */
private[parsing] trait ModuleParser {
  this: ProcessorParser & DomainParser & AdaptorParser & ContextParser & EntityParser & EpicParser &
    FunctionParser & HandlerParser & ProjectorParser & RepositoryParser & SagaParser &
    StreamingParser & TypeParser & Readability & CommonParser =>

  private def moduleInclude[u: P]: P[Include[ModuleContents]] = {
    include[u, ModuleContents]((p: P[?]) => moduleContents(using p.asInstanceOf[P[u]]))
  }

  /** Any top-level definition. Shared with the deprecated `nebula` entry point, which has exactly
    * the same content model.
    */
  def moduleContent[u: P]: P[ModuleContents] =
    P(
      bastImport | adaptor | author | comment | connector | constant | context | domain | entity |
        epic | function | invariant | module | projector | relationship | repository | saga |
        streamlet | typeDef | user | versionDef | copyrightDef
    ).asInstanceOf[P[ModuleContents]]

  def moduleContents[u: P]: P[Seq[ModuleContents]] = {
    P(moduleContent | moduleInclude[u]).asInstanceOf[P[ModuleContents]].rep(1)
  }

  def module[u: P]: P[Module] = {
    P(
      Index ~ Keywords.module ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[ModuleContents]) | moduleContents) ~
        close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Module(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
