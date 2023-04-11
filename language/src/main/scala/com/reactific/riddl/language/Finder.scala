/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*

case class Finder(root: Definition) {

  def find(select: Definition => Boolean): Seq[Definition] = {
    Folding.foldEachDefinition(root, root, Seq.empty[Definition]) {
      case (_, definition, state) =>
        if select(definition) then state :+ definition else state
    }
  }

  type DefWithParents = Seq[(Definition, Seq[Definition])]

  def findWithParents(
    select: Definition => Boolean
  ): DefWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(Definition, Seq[Definition])])(root) {
      case (state, definition, parents) =>
        if select(definition) then state :+ (definition -> parents) else state
    }
  }

  def findEmpty: DefWithParents = findWithParents(_.isEmpty)
}
