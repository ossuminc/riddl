/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import scala.collection.mutable

object Folding {

  private type SimpleDispatch[S] = (Container[Definition], Definition, S) => S

  def foldEachDefinition[S](
    parent: Definition,
    child: Definition,
    state: S
  )(f: SimpleDispatch[S]): S = {
    child match {
      case definition: LeafDefinition => f(parent, definition, state)
      case definition: Definition =>
        val result = f(parent, child, state)
        definition.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S](definition, child, next)(f)
        }
    }
  }

  final def foldLeftWithStack[S](
    value: S,
    parents: mutable.Stack[Definition] = mutable.Stack.empty[Definition]
  )(top: Definition)(f: (S, Definition, Seq[Definition]) => S): S = {
    val initial = f(value, top, parents.toSeq)
    parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, definition) =>
        definition match {
          case i: Include[Definition] @unchecked =>
            i.contents.foldLeft(next) {
              case (n, d: LeafDefinition) => f(n, d, parents.toSeq)
              case (n, cd: Definition)    => foldLeftWithStack(n, parents)(cd)(f)
            }
          case d: LeafDefinition => f(next, d, parents.toSeq)
          case c: Definition     => foldLeftWithStack(next, parents)(c)(f)
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
