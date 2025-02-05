/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import scalajs.js.annotation.*

import fastparse.*
import fastparse.MultiLineWhitespace.*

trait RootParser { this: ModuleParser & CommonParser & ParsingContext =>

  private def rootInclude[u: P]: P[Include[RootContents]] = {
    include[u, RootContents](rootContents(_))
  }

  private def rootContent[u: P]: P[RootContents] = {
    P(moduleContent | module | rootInclude[u]).asInstanceOf[P[RootContents]]
  }

  private def rootContents[u: P]: P[Seq[RootContents]] =
    P(rootContent).rep(1)

  def root[u: P]: P[Root] = {
    P(Start ~ Index ~ rootContents ~ Index ~ End).map { case (start, contents: Seq[RootContents], end) =>
      Root(at(start, end), contents.toContents)
    }
  }
}
