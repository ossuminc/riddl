/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

case class Finder(root: Container[RiddlValue]) {

  def find(select: RiddlValue => Boolean): Seq[RiddlValue] = {
    Folding.foldEachValue(root, root, Seq.empty[RiddlValue]) {
      case (_, definition, state) =>
        if select(definition) then state :+ definition else state
    }
  }

  type ValueWithParents = Seq[(RiddlValue, Seq[RiddlValue])]

  def findWithParents(
    select: RiddlValue => Boolean
  ): ValueWithParents = {
    Folding.foldLeftWithStack(Seq.empty[(RiddlValue, Seq[RiddlValue])])(root) {
      case (state, value, parents) =>
        if select(value) then state :+ (value -> parents) else state
    }
  }

  def findEmpty: ValueWithParents = findWithParents(_.isEmpty)
}
