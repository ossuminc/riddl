/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import scalajs.js.annotation.*

import fastparse.*
import fastparse.MultiLineWhitespace.*

trait RootParser { this: ModuleParser & DomainParser & CommonParser & ParsingContext =>

  private def rootInclude[u: P]: P[Include[RootContents]] = {
    include[u, RootContents]((p: P[?]) => rootContents(using p.asInstanceOf[P[u]]))
  }

  // bastImport is inherited from CommonParser

  /** A Root is the file parse-root, not the reuse unit, so it stays narrow: only Domains, Authors
    * and Comments sit directly in it. Wide, flat collections of any definition belong in a
    * [[AST.Module]] (see [[ModuleParser.moduleContent]]).
    */
  private def rootContent[u: P]: P[RootContents] = {
    P(bastImport | domain | author | versionDef | copyrightDef | comment | module | rootInclude[u])
      .asInstanceOf[P[RootContents]]
  }

  private def rootContents[u: P]: P[Seq[RootContents]] =
    P(rootContent).rep(1)

  def root[u: P]: P[Root] = {
    P(Start ~ Index ~ rootContents ~ Index ~ End).map {
      case (start, contents: Seq[RootContents], end) =>
        Root(at(start, end), contents.toContents)
    }
  }
}
