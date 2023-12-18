/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import scala.collection.mutable

object Folding {

  private type SimpleDispatch[S] = (Container[RiddlValue], RiddlValue, S) => S

  def foldEachValue[S](
    parent: Container[RiddlValue],
    child: RiddlValue,
    state: S
  )(f: SimpleDispatch[S]): S = {
    child match {
      case definition: Container[RiddlValue] =>
        val result = f(parent, child, state)
        definition.contents.foldLeft(result) { case (next, child) =>
          foldEachValue[S](definition, child, next)(f)
        }
      case definition: RiddlValue => f(parent, definition, state)
    }
  }

  final def foldLeftWithStack[S](
    value: S,
    parents: mutable.Stack[RiddlValue] = mutable.Stack.empty[RiddlValue]
  )(top: Container[RiddlValue])(f: (S, RiddlValue, Seq[RiddlValue]) => S): S = {
    val initial = f(value, top, parents.toSeq)
    parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, definition) =>
        definition match {
          case i: Include[Definition] @unchecked =>
            i.contents.foldLeft(next) {
              case (n, cd: Container[RiddlValue])    => foldLeftWithStack(n, parents)(cd)(f)
              case (n, d: RiddlValue) => f(n, d, parents.toSeq)
            }
          case c: Container[RiddlValue]     => foldLeftWithStack(next, parents)(c)(f)
          case d: RiddlValue => f(next, d, parents.toSeq)
        }
      }
    } finally { parents.pop() }
  }

  final def foldAround[S](
    value: S,
    top: Definition,
    folder: Folder[S],
    parents: mutable.Stack[Definition] = mutable.Stack.empty[Definition]
  ): S = {
    // Let them know a container is being opened
    val startState = folder.openContainer(value, top, parents.toSeq)
    parents.push(top)
    val middleState = top.contents.foldLeft(startState) {
      case (next, definition: LeafDefinition) =>
        // Leaf node so mention it
        parents.push(definition)
        val st = folder.doDefinition(next, definition, parents.toSeq)
        parents.pop()
        st
      case (next, container: Definition) =>
        // Container node so recurse
        foldAround(next, container, folder, parents)
    }
    // Let them know a container is being closed
    parents.pop()
    folder.closeContainer(middleState, top, parents.toSeq)
  }

  trait Folder[STATE] {
    def openContainer(
      state: STATE,
      container: Definition,
      parents: Seq[Definition]
    ): STATE

    def doDefinition(
      state: STATE,
      definition: Definition,
      parents: Seq[Definition]
    ): STATE

    def closeContainer(
      state: STATE,
      container: Definition,
      parents: Seq[Definition]
    ): STATE
  }

}
