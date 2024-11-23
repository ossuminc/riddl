/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing production rules for Modules
  * {{{
  *   Root = Comment | Domain | Module | Author
  *   Module = Root | Context | User | Epic | Author | Application | Saga
  *   Domain = VitalDefinition | Domain | Context | User | Epic | Author | Application |  Saga
  * }}}
  */
private[parsing] trait NebulaParser {
  this: ProcessorParser & DomainParser & AdaptorParser & ApplicationParser & ContextParser & EntityParser & EpicParser &
    FunctionParser & HandlerParser & ModuleParser & ProjectorParser & RepositoryParser & RootParser & SagaParser &
    StreamingParser & TypeParser & Readability & CommonParser =>

  private def nebulaContent[u: P]: P[NebulaContents] =
    P(
      adaptor | author | connector | constant | context | domain | entity | epic | function |
        invariant | module | projector | relationship | repository | saga | streamlet | typeDef | user
    ).map { (r: RiddlValue) => r.asInstanceOf[NebulaContents] }

  private def nebulaContents[u: P]: P[Seq[NebulaContents]] = P(nebulaContent).rep(0)

  def nebula[u: P]: P[Nebula] = {
    P(
      Start ~ Index ~ nebulaContents ~ Index ~ End
    ).map { case (start: Int, contents: Seq[NebulaContents], end: Int) =>
      Nebula(at(start, end), contents.toContents)
    }
  }
}
