/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import scala.collection.mutable

object Folding {

  private type SimpleDispatch[S, V <: RiddlValue] = (Container[V], V, S) => S

  def foldEachDefinition[S, V <: RiddlValue](
    parent: Container[V],
    child: V,
    state: S
  )(f: SimpleDispatch[S, V]): S = {
    child match {
      case value: V if value.isContainer && value.nonEmpty =>
        val result = f(parent, child, state)
        val container = value.asInstanceOf[Container[V]]
        container.contents.foldLeft(result) { case (next, child) =>
          foldEachDefinition[S, V](container, child, next)(f)
        }
      case value: V => f(parent, value, state)
    }
  }

  final def foldLeftWithStack[S, CT <: RiddlValue](
    value: S,
    parents: mutable.Stack[Container[CT]] = mutable.Stack.empty[Container[CT]]
  )(top: Container[CT])(f: (S, CT | Container[CT], Seq[Container[CT]]) => S): S = {
    require(top.isContainer) // FIXME: Remove after debugging
    val initial = f(value, top, parents.toSeq)
    if !top.isAnonymous then parents.push(top)
    try {
      top.contents.foldLeft(initial) { (next, value) =>
        value match {
          case c: Container[CT] @unchecked if c.nonEmpty => foldLeftWithStack(next, parents)(c)(f)
          case v: RiddlValue                          => f(next, v, parents.toSeq)
        }
      }
    } finally {
      if !top.isAnonymous then parents.pop()
    }
  }
}
