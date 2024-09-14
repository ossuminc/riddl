/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

import scala.reflect.{ClassTag, classTag}
import scalajs.js.annotation._

/** The context for finding things within a given [[Container]] of [[com.ossuminc.riddl.language.AST.RiddlValue]] 
  * as found in the AST model. This  provides the ability to find values in the model by traversing it and looking
 * for the matching condition.
  * @param root
  *   The container of RiddlValues to traverse for the sought condition
  */
@JSExportTopLevel("Finder")
case class Finder[CV <: ContentValues](root: Container[CV]) {

  import scala.reflect.ClassTag

  /** Search the `root` for a [[AST.RiddlValue]] that matches the boolean expression
    *
    * @param select
    *   The boolean expression to search for
    *
    * @return
    *   A [[scala.Seq]] of the matching [[AST.RiddlValue]]
    */
  @JSExport
  def find(select: CV => Boolean): Seq[CV] = {
    Folding.foldEachDefinition[Seq[CV],CV](root, Seq.empty[CV]) { case (state: Seq[CV], value: CV) =>
      if select(value) then state :+ value else state
    }
  }

  /** Search the [[root]] for a certain kind of [[AST.RiddlValue]] and return those */
  @JSExport
  def findByType[T <: AST.RiddlValue: ClassTag]: Seq[T] = {
    import scala.reflect.classTag
    val lookingFor = classTag[T].runtimeClass
    val result = find { (value: RiddlValue) => lookingFor.isAssignableFrom(value.getClass) }
    result.asInstanceOf[Seq[T]]
  }

  /** The return value for the [[Finder.findWithParents()]] function */
  type DefWithParents[T <: RiddlValue] = Seq[(T, Parents)]

  /** Find a matching set of [[AST.RiddlValue]] but return them with their parents
    *
    * @param select
    *   The boolean expression derived from a candidate [[AST.RiddlValue]] that selects it to the result set
    * @return
    *   A [[Finder#DefWithParents]] that returns a [[scala.Seq]] of two-tuples with the [[AST.RiddlValue]] a a
    *   [[scala.Seq]] of the parents of that value.
    */
  @JSExport
  def findWithParents[T <: RiddlValue : ClassTag](
    select: T => Boolean
  ): DefWithParents[T] = {
    import scala.collection.mutable
    val lookingFor = classTag[T].runtimeClass
    Folding.foldLeftWithStack[Seq[(T, Parents)],CV](
      Seq.empty[(T, Parents)],
      root,
      ParentStack.empty
    ) { case (state, definition: CV, parents) =>
      if lookingFor.isAssignableFrom(definition.getClass) then
        val value: T = definition.asInstanceOf[T]
        if select(value) then
          state :+ (value -> parents)
        else
          state
      else
        state
    }
  }

  /** Find definitions that are empty
    *
    * @return
    *   A [[scala.Seq]] of [[AST.RiddlValue]], along with their parents that are empty
    */
  @JSExport def findEmpty: DefWithParents[Definition] = findWithParents[Definition](_.isEmpty)
}

object Finder {
  def apply[CV <: ContentValues](contents: Contents[CV]): Finder[CV] = {
    val container = SimpleContainer[CV](contents)
    Finder[CV](container)
  }
}
