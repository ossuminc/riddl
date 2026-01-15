/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import scalajs.js.annotation.*

import fastparse.*
import fastparse.MultiLineWhitespace.*

trait RootParser { this: ModuleParser & CommonParser & ParsingContext =>

  private def rootInclude[u: P]: P[Include[RootContents]] = {
    include[u, RootContents](rootContents(_))
  }

  /** Parse a BAST import statement: `import "path/to/file.bast" as namespace` */
  private def bastImport[u: P]: P[BASTImport] = {
    P(Index ~ Keywords.import_ ~ literalString ~ as ~ identifier ~ Index).map {
      case (start, path, namespace, end) =>
        doBASTImport(at(start, end), path, namespace)
    }
  }

  private def rootContent[u: P]: P[RootContents] = {
    P(bastImport | moduleContent | module | rootInclude[u]).asInstanceOf[P[RootContents]]
  }

  private def rootContents[u: P]: P[Seq[RootContents]] =
    P(rootContent).rep(1)

  def root[u: P]: P[Root] = {
    P(Start ~ Index ~ rootContents ~ Index ~ End).map { case (start, contents: Seq[RootContents], end) =>
      Root(at(start, end), contents.toContents)
    }
  }
}
