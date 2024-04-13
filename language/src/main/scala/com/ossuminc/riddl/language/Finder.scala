/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

case class Finder(root: Container[RiddlValue]) {

  def find(select: RiddlValue => Boolean): Seq[RiddlValue] = {
    Folding.foldEachDefinition(root, root, Seq.empty) { case (_, value, state) =>
      if select(value) then state :+ value else state
    }
  }

  type DefWithParents = Seq[(RiddlValue, Seq[RiddlValue])]

  def findWithParents(
    select: RiddlValue => Boolean
  ): DefWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(RiddlValue, Seq[RiddlValue])])(root) { case (state, definition, parents) =>
      if select(definition) then state :+ (definition -> parents) else state
    }
  }

  def findEmpty: DefWithParents = findWithParents(_.isEmpty)
}
