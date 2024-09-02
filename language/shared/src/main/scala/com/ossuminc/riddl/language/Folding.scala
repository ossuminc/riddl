/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

import scala.collection.mutable
import scala.scalajs.js.annotation._

/** An object for distinguishing several functions as ways to fold the model */
@JSExportTopLevel("Folding")
object Folding {

  private type SimpleDispatch[S, V <: RiddlValue] = (Container[V], V, S) => S

  /** Folding with state from an element of type V
    *
    * @param parent
    *   The parent node of V which must be its direct container
    * @param child
    *   The node to fold through
    * @param state
    *   Initial value of arbitrary type `S`` that can be used to fold the nodes into and provides the result type
    * @param f
    *   The folding function which takes 3 arguments and returns an `S` (list the initial `state`)
    * @tparam S
    *   The type of the state for folding
    * @tparam V
    *   The type of the element being folded
    * @return
    *   The resulting state of type `S`
    */
  @JSExport
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

  /** A Typical foldLeft as with [[scala.collection.Seq]] but utilizing a stack of parents as well.
    * @param value
    *   The node at which folding starts
    * @param parents
    *   The parents of the `value` node
    * @param top
    *   The containing top node of `value`
    * @param f
    *   The folder function which is passed the state [S], the node or its container, and the list of parents
    * @tparam S
    *   The type of the state
    * @tparam CT
    *   The type of nodes to fold over
    * @return
    *   The folded state
    * @see
    *   [[scala.collection.Seq.foldLeft()]]
    */
  @JSExport final def foldLeftWithStack[S, CT <: RiddlValue](
    zeroValue: S,
    top: Container[CT],
    parents: ParentStack
  )(f: (S, CT | Container[CT], Parents) => S ): S = {
    val initial = f(zeroValue, top, parents.toParents)
    top match
      case p: Parent =>  parents.push(p)
      case _ => ()
    end match  
    try {
      top.contents.foldLeft(initial) { (next, value) =>
        value match {
          case c: Container[CT] @unchecked if c.nonEmpty => foldLeftWithStack(next, c, parents)(f)
          case v: CT                             => f(next, v, parents.toParents)
        }
      }
    } finally {
      top match
        case p: Parent => parents.pop()
        case _ => ()
      end match
    }
  }
}
